#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="${HERMES_NAMESPACE:-hermes-dev}"
ESO_NS="${ESO_NAMESPACE:-external-secrets}"
TIMEOUT="${ESO_SYNC_TIMEOUT:-300}"

REQUIRED_SECRETS=(
  gateway-secrets
  auth-service-secrets
  user-service-secrets
  auth-postgres-secrets
  user-postgres-secrets
  rabbitmq-secrets
  keycloak-secrets
)

if ! kubectl get externalsecret -n "$NAMESPACE" --no-headers 2>/dev/null | grep -q .; then
  echo "No ExternalSecrets in $NAMESPACE; skipping Vault secret sync wait."
  exit 0
fi

wait_for() {
  local description=$1
  shift
  echo "Waiting for $description (timeout ${TIMEOUT}s)..."
  if ! "$@" --timeout="${TIMEOUT}s" 2>/dev/null; then
    echo "Timed out waiting for $description."
    kubectl get externalsecret -n "$NAMESPACE" 2>/dev/null || true
    kubectl get clustersecretstore vault-hermes-dev -o yaml 2>/dev/null | tail -30 || true
    exit 1
  fi
}

wait_for "External Secrets controller" \
  kubectl wait deployment/external-secrets -n "$ESO_NS" --for=condition=Available

wait_for "External Secrets webhook" \
  kubectl wait deployment/external-secrets-webhook -n "$ESO_NS" --for=condition=Available

echo "Waiting for ClusterSecretStore vault-hermes-dev to be ready..."
elapsed=0
while true; do
  ready="$(kubectl get clustersecretstore vault-hermes-dev -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}' 2>/dev/null || true)"
  if [[ "$ready" == "True" ]]; then
    break
  fi
  if (( elapsed >= TIMEOUT )); then
    echo "Timed out waiting for ClusterSecretStore vault-hermes-dev."
    kubectl describe clustersecretstore vault-hermes-dev 2>/dev/null || true
    exit 1
  fi
  sleep 2
  elapsed=$((elapsed + 2))
done

for es in "${REQUIRED_SECRETS[@]}"; do
  wait_for "ExternalSecret $es" \
    kubectl wait "externalsecret/$es" -n "$NAMESPACE" --for=condition=Ready
done

for secret in "${REQUIRED_SECRETS[@]}"; do
  echo "Waiting for Kubernetes secret $secret..."
  elapsed=0
  while ! kubectl get secret "$secret" -n "$NAMESPACE" >/dev/null 2>&1; do
    if (( elapsed >= TIMEOUT )); then
      echo "Timed out waiting for secret $secret in $NAMESPACE."
      kubectl describe "externalsecret/$secret" -n "$NAMESPACE" 2>/dev/null || true
      exit 1
    fi
    sleep 2
    elapsed=$((elapsed + 2))
  done
done

echo "All Vault-backed secrets are synced in $NAMESPACE."
