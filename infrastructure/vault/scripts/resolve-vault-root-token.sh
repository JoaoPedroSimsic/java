#!/usr/bin/env bash
set -euo pipefail

VAULT_NS="${VAULT_NS:-vault}"
VAULT_POD="${VAULT_POD:-vault-0}"

if [[ -n "${VAULT_ROOT_TOKEN:-}" ]]; then
  echo "$VAULT_ROOT_TOKEN"
  exit 0
fi

if ! kubectl get pod -n "$VAULT_NS" "$VAULT_POD" -o jsonpath='{.status.phase}' 2>/dev/null | grep -q Running; then
  exit 1
fi

token="$(
  kubectl logs -n "$VAULT_NS" "$VAULT_POD" 2>/dev/null \
    | sed -n 's/^Root Token: \([^[:space:]]*\).*/\1/p' \
    | tail -1 \
    || true
)"
if [[ -n "$token" ]]; then
  echo "$token"
  exit 0
fi

token="$(
  kubectl exec -n "$VAULT_NS" "$VAULT_POD" -- sh -c \
    'VAULT_ADDR=http://127.0.0.1:8200 vault print token' 2>/dev/null \
    || true
)"
if [[ -n "$token" ]]; then
  echo "$token"
  exit 0
fi

exit 1
