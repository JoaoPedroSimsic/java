#!/usr/bin/env bash
set -euo pipefail

ENV="${HERMES_ENV:-staging}"

if [[ ! "$ENV" =~ ^(dev|staging|prod)$ ]]; then
  echo "HERMES_ENV must be dev, staging, or prod (got: $ENV)"
  exit 1
fi

NAMESPACE="${HERMES_NAMESPACE:-hermes-${ENV}}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
K8S_ROOT="$REPO_ROOT/k8s"
TIMEOUT="${SMOKE_TIMEOUT:-600}"

REQUIRED_SECRETS=(
  gateway-secrets
  auth-service-secrets
  user-service-secrets
  auth-postgres-secrets
  user-postgres-secrets
  rabbitmq-secrets
  keycloak-secrets
)

REQUIRED_DEPLOYMENTS=(
  auth-service
  user-service
  http-gateway
)

declare -A SECRET_KEYS=(
  [gateway-secrets]="GATEWAY_SECRET"
  [auth-service-secrets]="APP_PASSWORD FLYWAY_PASSWORD RABBITMQ_PASSWORD"
  [user-service-secrets]="GATEWAY_SECRET APP_PASSWORD FLYWAY_PASSWORD"
  [auth-postgres-secrets]="POSTGRES_PASSWORD APP_PASSWORD FLYWAY_PASSWORD"
  [user-postgres-secrets]="POSTGRES_PASSWORD APP_PASSWORD FLYWAY_PASSWORD"
  [rabbitmq-secrets]="RABBITMQ_PASSWORD"
  [keycloak-secrets]="KEYCLOAK_ADMIN_PASSWORD"
)

echo "=== Smoke test: ESO-managed secrets ($ENV / $NAMESPACE) ==="

if [[ "$ENV" != "dev" ]]; then
  echo "Checking staging/prod overlays have no secretGenerator + secrets.env..."
  if grep -rEl 'secretGenerator:' "$K8S_ROOT"/*/overlays/"$ENV" "$K8S_ROOT"/services/*/overlays/"$ENV" \
      "$K8S_ROOT"/gateways/*/overlays/"$ENV" "$K8S_ROOT"/shared/*/overlays/"$ENV" 2>/dev/null | grep -q .; then
    echo "FAIL: secretGenerator found under overlays/${ENV}/"
    grep -rEl 'secretGenerator:' "$K8S_ROOT"/*/overlays/"$ENV" "$K8S_ROOT"/services/*/overlays/"$ENV" \
      "$K8S_ROOT"/gateways/*/overlays/"$ENV" "$K8S_ROOT"/shared/*/overlays/"$ENV" 2>/dev/null || true
    exit 1
  fi
  if grep -rEl 'secrets\.env' "$K8S_ROOT"/*/overlays/"$ENV" "$K8S_ROOT"/services/*/overlays/"$ENV" \
      "$K8S_ROOT"/gateways/*/overlays/"$ENV" "$K8S_ROOT"/shared/*/overlays/"$ENV" 2>/dev/null | grep -q .; then
    echo "FAIL: secrets.env reference found under overlays/${ENV}/"
    exit 1
  fi
  echo "OK: no plaintext secretGenerator in overlays/${ENV}/"
fi

for es in "${REQUIRED_SECRETS[@]}"; do
  ready="$(kubectl get externalsecret "$es" -n "$NAMESPACE" \
    -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}' 2>/dev/null || true)"
  if [[ "$ready" != "True" ]]; then
    echo "FAIL: ExternalSecret $es is not Ready (status: ${ready:-missing})"
    kubectl describe externalsecret "$es" -n "$NAMESPACE" 2>/dev/null | tail -20 || true
    exit 1
  fi
  echo "OK: ExternalSecret $es Ready"
done

for secret in "${REQUIRED_SECRETS[@]}"; do
  if ! kubectl get secret "$secret" -n "$NAMESPACE" >/dev/null 2>&1; then
    echo "FAIL: Kubernetes Secret $secret missing in $NAMESPACE"
    exit 1
  fi
  for key in ${SECRET_KEYS[$secret]}; do
    if ! kubectl get secret "$secret" -n "$NAMESPACE" \
      -o "jsonpath={.data.${key}}" 2>/dev/null | grep -q .; then
      echo "FAIL: Secret $secret missing key $key"
      exit 1
    fi
  done
  echo "OK: Secret $secret has expected keys"
done

for dep in "${REQUIRED_DEPLOYMENTS[@]}"; do
  echo "Waiting for deployment/$dep Available (timeout ${TIMEOUT}s)..."
  if ! kubectl wait deployment/"$dep" -n "$NAMESPACE" \
    --for=condition=Available --timeout="${TIMEOUT}s" 2>/dev/null; then
    echo "FAIL: deployment/$dep not Available"
    kubectl get pods -n "$NAMESPACE" -l "app.kubernetes.io/name=$dep" 2>/dev/null || true
    exit 1
  fi
  echo "OK: deployment/$dep Available"
done

check_health() {
  local service=$1
  local port=$2
  local path=$3
  echo "Checking $service health..."
  local response
  response="$(kubectl run "smoke-${service}-$$" -n "$NAMESPACE" --rm -i --restart=Never \
    --image=curlimages/curl:8.5.0 --command -- \
    curl -sf "http://${service}:${port}${path}" 2>/dev/null || true)"
  if [[ -z "$response" ]]; then
    echo "FAIL: $service health check returned no response"
    return 1
  fi
  for leak in GATEWAY_SECRET APP_PASSWORD FLYWAY_PASSWORD KEYCLOAK_ADMIN_PASSWORD COGNITO_CLIENT_SECRET; do
    if echo "$response" | grep -q "$leak"; then
      echo "FAIL: $service health response may leak secret key name: $leak"
      return 1
    fi
  done
  echo "OK: $service health responded without obvious secret leakage"
}

check_health http-gateway 8081 /actuator/health
check_health auth-service 8083 /actuator/health
check_health user-service 8084 /actuator/health

echo "=== All smoke checks passed for $ENV ==="
