#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
VAULT_NS="${VAULT_NS:-vault}"
VAULT_POD="${VAULT_POD:-vault-0}"
ENV="${HERMES_ENV:-dev}"
VAULT_SECRETS_DIR="$REPO_ROOT/infrastructure/vault/secrets"

ENV_FILE="$REPO_ROOT/.env"
[[ -f "$REPO_ROOT/.env.$ENV" ]] && ENV_FILE="$REPO_ROOT/.env.$ENV"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Environment file not found: $ENV_FILE"
  echo "Copy .env.example to .env and fill in values."
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

require_var() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "Required variable '$name' is missing or empty in $ENV_FILE"
    exit 1
  fi
}

collect_required_vars() {
  local file line key value
  for file in "$VAULT_SECRETS_DIR"/*.env; do
    [[ -f "$file" ]] || continue
    while IFS= read -r line || [[ -n "$line" ]]; do
      [[ "$line" =~ ^[[:space:]]*# ]] && continue
      [[ -z "${line//[[:space:]]/}" ]] && continue
      key="${line%%=*}"
      value="${line#*=}"
      [[ "$key" == "$line" ]] && continue
      if [[ "$value" =~ ^\$([A-Za-z_][A-Za-z0-9_]*)$ ]]; then
        echo "${BASH_REMATCH[1]}"
      fi
    done < "$file"
  done | sort -u
}

while IFS= read -r var; do
  require_var "$var"
done < <(collect_required_vars)

if ! ROOT_TOKEN="$(bash "$SCRIPT_DIR/resolve-vault-root-token.sh")"; then
  echo "Could not determine Vault root token. Export VAULT_ROOT_TOKEN or inspect:"
  echo "  kubectl logs -n $VAULT_NS $VAULT_POD"
  exit 1
fi

if ! kubectl get pod -n "$VAULT_NS" "$VAULT_POD" -o jsonpath='{.status.phase}' 2>/dev/null | grep -q Running; then
  echo "Vault pod $VAULT_NS/$VAULT_POD is not Running."
  exit 1
fi

vault_kv_put() {
  local path="$1"
  shift
  echo "Writing secret/$path ..."
  kubectl exec -n "$VAULT_NS" "$VAULT_POD" -- env \
    VAULT_ADDR="http://127.0.0.1:8200" \
    VAULT_TOKEN="$ROOT_TOKEN" \
    vault kv put "secret/$path" "$@"
}

PREFIX="hermes/$ENV"

shopt -s nullglob
secret_files=("$VAULT_SECRETS_DIR"/*.env)
shopt -u nullglob

if [[ ${#secret_files[@]} -eq 0 ]]; then
  echo "No secret definition files found in $VAULT_SECRETS_DIR"
  exit 1
fi

for secret_file in "${secret_files[@]}"; do
  vault_path="$(head -1 "$secret_file" | sed 's/^# vault_path=//')"
  if [[ -z "$vault_path" || "$vault_path" == "$(head -1 "$secret_file")" ]]; then
    echo "Missing '# vault_path=...' header in $secret_file"
    exit 1
  fi

  kv_args=()
  while IFS= read -r line || [[ -n "$line" ]]; do
    [[ "$line" =~ ^[[:space:]]*# ]] && continue
    [[ -z "${line//[[:space:]]/}" ]] && continue

    key="${line%%=*}"
    value="${line#*=}"
    if [[ "$key" == "$line" ]]; then
      echo "Invalid line in $secret_file (expected key=value): $line"
      exit 1
    fi

    if [[ "$value" =~ ^\$([A-Za-z_][A-Za-z0-9_]*)$ ]]; then
      ref="${BASH_REMATCH[1]}"
      resolved="${!ref:-}"
      if [[ -z "$resolved" ]]; then
        echo "Unresolved reference \$$ref in $secret_file"
        exit 1
      fi
      kv_args+=("$key=$resolved")
    else
      kv_args+=("$key=$value")
    fi
  done < <(tail -n +2 "$secret_file")

  vault_kv_put "$PREFIX/$vault_path" "${kv_args[@]}"
done

echo "Done. Vault paths seeded under secret/$PREFIX/"
echo "Next: kubectl get externalsecret,secret -n hermes-dev"
