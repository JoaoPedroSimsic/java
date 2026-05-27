#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OVERLAY="$(cd "$SCRIPT_DIR/../../k8s/shared/vault/overlays/dev" && pwd)"

echo "Deploying Vault (outside Skaffold status check)..."
kubectl kustomize "$OVERLAY" \
  --enable-helm \
  --load-restrictor=LoadRestrictionsNone \
  | kubectl apply --server-side --force-conflicts -f -

wait_for_vault() {
  local timeout="${1:-300s}"
  kubectl wait pod/vault-0 -n vault --for=condition=Ready "--timeout=$timeout"
}

echo "Waiting for vault-0..."
if ! wait_for_vault 120s 2>/dev/null; then
  echo "vault-0 not ready after 120s; recycling pod..."
  kubectl delete pod vault-0 -n vault --ignore-not-found --wait=true
  wait_for_vault 300s
fi

echo "Vault is ready."
