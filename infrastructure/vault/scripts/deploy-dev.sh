#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OVERLAY="$(cd "$SCRIPT_DIR/../../k8s/shared/vault/overlays/dev" && pwd)"

echo "Deploying Vault (outside DevSpace status check)..."
kubectl kustomize "$OVERLAY" \
  --enable-helm \
  --load-restrictor=LoadRestrictionsNone \
  | kubectl apply --server-side --force-conflicts -f -

wait_for_vault() {
  local deadline="${1:-300}"
  local elapsed=0
  while (( elapsed < deadline )); do
    if kubectl get pod vault-0 -n vault --no-headers 2>/dev/null | grep -q .; then
      if kubectl wait pod/vault-0 -n vault --for=condition=Ready --timeout=10s 2>/dev/null; then
        return 0
      fi
    fi
    sleep 5
    (( elapsed += 5 ))
  done
  return 1
}

echo "Waiting for vault-0..."
if ! wait_for_vault 300; then
  echo "vault-0 not ready after 300s; recycling pod and retrying..."
  kubectl delete pod vault-0 -n vault --ignore-not-found --wait=true
  wait_for_vault 300 || { echo "vault-0 still not ready after recycle"; exit 1; }
fi

echo "Vault is ready."
