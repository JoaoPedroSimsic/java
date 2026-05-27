#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
POLICY_DIR="$(cd "$SCRIPT_DIR/../policies" && pwd)"
VAULT_NS="${VAULT_NS:-vault}"
VAULT_POD="${VAULT_POD:-vault-0}"
ESO_ROLE="${ESO_VAULT_ROLE:-external-secrets}"
ESO_SA="${ESO_SERVICE_ACCOUNT:-external-secrets}"
ESO_NS="${ESO_NAMESPACE:-external-secrets}"

if ! ROOT_TOKEN="$(bash "$SCRIPT_DIR/resolve-vault-root-token.sh")"; then
  echo "Could not determine Vault root token. Export VAULT_ROOT_TOKEN or inspect:"
  echo "  kubectl logs -n $VAULT_NS $VAULT_POD"
  exit 1
fi

if ! kubectl get pod -n "$VAULT_NS" "$VAULT_POD" -o jsonpath='{.status.phase}' 2>/dev/null | grep -q Running; then
  echo "Vault pod $VAULT_NS/$VAULT_POD is not Running."
  exit 1
fi

echo "Applying policy hermes-apps-read..."
kubectl exec -n "$VAULT_NS" "$VAULT_POD" -i -- env \
  VAULT_ADDR="http://127.0.0.1:8200" \
  VAULT_TOKEN="$ROOT_TOKEN" \
  vault policy write hermes-apps-read - <"$POLICY_DIR/hermes-apps-read.hcl"

echo "Ensuring KV v2 secrets engine at secret/..."
kubectl exec -n "$VAULT_NS" "$VAULT_POD" -- env \
  VAULT_ADDR="http://127.0.0.1:8200" \
  VAULT_TOKEN="$ROOT_TOKEN" \
  sh -c 'vault secrets enable -path=secret kv-v2' 2>/dev/null || true

echo "Enabling Kubernetes auth (idempotent)..."
kubectl exec -n "$VAULT_NS" "$VAULT_POD" -- env \
  VAULT_ADDR="http://127.0.0.1:8200" \
  VAULT_TOKEN="$ROOT_TOKEN" \
  vault auth enable kubernetes 2>/dev/null || true

echo "Configuring Kubernetes auth backend..."
kubectl exec -n "$VAULT_NS" "$VAULT_POD" -- env \
  VAULT_ADDR="http://127.0.0.1:8200" \
  VAULT_TOKEN="$ROOT_TOKEN" \
  sh -c 'vault write auth/kubernetes/config \
    kubernetes_host="https://${KUBERNETES_SERVICE_HOST}:443" \
    token_reviewer_jwt="$(cat /var/run/secrets/kubernetes.io/serviceaccount/token)" \
    kubernetes_ca_cert=@/var/run/secrets/kubernetes.io/serviceaccount/ca.crt \
    issuer="https://kubernetes.default.svc.cluster.local"'

echo "Writing auth role $ESO_ROLE for SA $ESO_SA/$ESO_NS..."
kubectl exec -n "$VAULT_NS" "$VAULT_POD" -- env \
  VAULT_ADDR="http://127.0.0.1:8200" \
  VAULT_TOKEN="$ROOT_TOKEN" \
  vault write "auth/kubernetes/role/$ESO_ROLE" \
  bound_service_account_names="$ESO_SA" \
  bound_service_account_namespaces="$ESO_NS" \
  policies=hermes-apps-read \
  ttl=24h

echo "Done. ClusterSecretStore vault-hermes-dev should use role: $ESO_ROLE"
echo "Next: make vault-seed  (populate KV from .env)"
