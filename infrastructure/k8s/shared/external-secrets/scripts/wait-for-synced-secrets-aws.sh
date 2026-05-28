#!/usr/bin/env bash
set -euo pipefail

ENV="${HERMES_ENV:-prod}"

if [[ ! "$ENV" =~ ^(staging|prod)$ ]]; then
  echo "HERMES_ENV must be staging or prod (got: $ENV)"
  exit 1
fi

NAMESPACE="${HERMES_NAMESPACE:-hermes-${ENV}}"
ESO_NS="${ESO_NAMESPACE:-external-secrets}"
STORE_NAME="aws-hermes-${ENV}"
TIMEOUT="${ESO_SYNC_TIMEOUT:-600}"

if ! kubectl get externalsecret -n "$NAMESPACE" --no-headers 2>/dev/null | grep -q .; then
  echo "No ExternalSecrets in $NAMESPACE; skipping AWS secret sync wait."
  exit 0
fi

mapfile -t REQUIRED_SECRETS < <(
  kubectl get externalsecret -n "$NAMESPACE" -o jsonpath='{.items[*].metadata.name}' \
    | tr ' ' '\n' | sort
)

if [[ ${#REQUIRED_SECRETS[@]} -eq 0 ]]; then
  echo "No ExternalSecrets found in $NAMESPACE; skipping sync wait."
  exit 0
fi

wait_for() {
  local description=$1
  shift
  echo "Waiting for $description (timeout ${TIMEOUT}s)..."
  if ! "$@" --timeout="${TIMEOUT}s" 2>/dev/null; then
    echo "Timed out waiting for $description."
    kubectl get externalsecret -n "$NAMESPACE" 2>/dev/null || true
    kubectl get clustersecretstore "$STORE_NAME" -o yaml 2>/dev/null | tail -30 || true
    exit 1
  fi
}

wait_for "External Secrets controller" \
  kubectl wait deployment/external-secrets -n "$ESO_NS" --for=condition=Available

wait_for "External Secrets webhook" \
  kubectl wait deployment/external-secrets-webhook -n "$ESO_NS" --for=condition=Available

echo "Waiting for ClusterSecretStore $STORE_NAME to be ready..."
elapsed=0
while true; do
  ready="$(kubectl get clustersecretstore "$STORE_NAME" -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}' 2>/dev/null || true)"
  if [[ "$ready" == "True" ]]; then
    break
  fi
  if (( elapsed >= TIMEOUT )); then
    echo "Timed out waiting for ClusterSecretStore $STORE_NAME."
    kubectl describe clustersecretstore "$STORE_NAME" 2>/dev/null || true
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

echo "All AWS-backed secrets are synced in $NAMESPACE."
