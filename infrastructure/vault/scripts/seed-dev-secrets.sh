#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
VAULT_NS="${VAULT_NS:-vault}"
VAULT_POD="${VAULT_POD:-vault-0}"
ENV="${HERMES_ENV:-dev}"

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

REQUIRED=(
  GATEWAY_SECRET
  RABBITMQ_USERNAME RABBITMQ_PASSWORD
  GITHUB_CLIENT_ID GITHUB_CLIENT_SECRET
  POSTGRES_USER POSTGRES_PASSWORD POSTGRES_DB
  APP_USER APP_PASSWORD FLYWAY_USER FLYWAY_PASSWORD
  KEYCLOAK_ADMIN KEYCLOAK_ADMIN_PASSWORD
)

for var in "${REQUIRED[@]}"; do
  require_var "$var"
done

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

vault_kv_put "$PREFIX/shared/jwt-signing-key" \
  value="$GATEWAY_SECRET"

vault_kv_put "$PREFIX/shared/rabbitmq" \
  username="$RABBITMQ_USERNAME" \
  password="$RABBITMQ_PASSWORD"

vault_kv_put "$PREFIX/shared/github-oauth" \
  client_id="$GITHUB_CLIENT_ID" \
  client_secret="$GITHUB_CLIENT_SECRET"

vault_kv_put "$PREFIX/services/auth-db/postgres" \
  POSTGRES_USER="$POSTGRES_USER" \
  POSTGRES_PASSWORD="$POSTGRES_PASSWORD" \
  POSTGRES_DB="$POSTGRES_DB" \
  APP_USER="$APP_USER" \
  APP_PASSWORD="$APP_PASSWORD" \
  FLYWAY_USER="$FLYWAY_USER" \
  FLYWAY_PASSWORD="$FLYWAY_PASSWORD"

vault_kv_put "$PREFIX/services/user-db/postgres" \
  POSTGRES_USER="$POSTGRES_USER" \
  POSTGRES_PASSWORD="$POSTGRES_PASSWORD" \
  POSTGRES_DB="$POSTGRES_DB" \
  APP_USER="$APP_USER" \
  APP_PASSWORD="$APP_PASSWORD" \
  FLYWAY_USER="$FLYWAY_USER" \
  FLYWAY_PASSWORD="$FLYWAY_PASSWORD"

vault_kv_put "$PREFIX/services/keycloak/keycloak-admin" \
  username="$KEYCLOAK_ADMIN" \
  password="$KEYCLOAK_ADMIN_PASSWORD"

echo "Done. Vault paths seeded under secret/$PREFIX/"
echo "Next: kubectl get externalsecret,secret -n hermes-dev"
