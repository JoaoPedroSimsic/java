#!/usr/bin/env bash
# Enable Vault database secrets engine + static role for auth-db APP_USER (Phase D, dev opt-in).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
POLICY_DIR="$(cd "$SCRIPT_DIR/../policies" && pwd)"
VAULT_NS="${VAULT_NS:-vault}"
VAULT_POD="${VAULT_POD:-vault-0}"
HERMES_NS="${HERMES_NAMESPACE:-hermes-dev}"
DB_HOST="${AUTH_DB_HOST:-auth-db}"
DB_NAME="${POSTGRES_DB:-postgres}"
STATIC_ROLE="${VAULT_DB_STATIC_ROLE:-hermes-auth-app-static}"
CONFIG_NAME="${VAULT_DB_CONFIG_NAME:-hermes-auth-db}"
ROTATION_PERIOD="${VAULT_DB_ROTATION_PERIOD:-24h}"

if ! ROOT_TOKEN="$(bash "$SCRIPT_DIR/resolve-vault-root-token.sh")"; then
  echo "Could not determine Vault root token."
  exit 1
fi

vault_exec() {
  kubectl exec -n "$VAULT_NS" "$VAULT_POD" -- env \
    VAULT_ADDR="http://127.0.0.1:8200" \
    VAULT_TOKEN="$ROOT_TOKEN" \
    "$@"
}

echo "Re-applying policy hermes-apps-read (includes database paths)..."
kubectl exec -n "$VAULT_NS" "$VAULT_POD" -i -- env \
  VAULT_ADDR="http://127.0.0.1:8200" \
  VAULT_TOKEN="$ROOT_TOKEN" \
  vault policy write hermes-apps-read - <"$POLICY_DIR/hermes-apps-read.hcl"

echo "Reading Postgres admin credentials from KV..."
POSTGRES_USER="$(vault_exec vault kv get -field=POSTGRES_USER "secret/hermes/dev/services/auth-db/postgres")"
POSTGRES_PASSWORD="$(vault_exec vault kv get -field=POSTGRES_PASSWORD "secret/hermes/dev/services/auth-db/postgres")"
APP_USER="$(vault_exec vault kv get -field=APP_USER "secret/hermes/dev/services/auth-db/postgres")"

CONNECTION_URL="postgresql://{{username}}:{{password}}@${DB_HOST}.${HERMES_NS}.svc.cluster.local:5432/${DB_NAME}?sslmode=disable"

echo "Enabling database secrets engine..."
vault_exec vault secrets enable database 2>/dev/null || true

echo "Configuring database connection $CONFIG_NAME -> ${DB_HOST}.${HERMES_NS}..."
vault_exec vault write "database/config/${CONFIG_NAME}" \
  plugin_name=postgresql-database-plugin \
  allowed_roles="${STATIC_ROLE}" \
  connection_url="$CONNECTION_URL" \
  username="$POSTGRES_USER" \
  password="$POSTGRES_PASSWORD"

echo "Creating static role $STATIC_ROLE for user $APP_USER (rotation ${ROTATION_PERIOD})..."
vault_exec vault write "database/static-roles/${STATIC_ROLE}" \
  db_name="$CONFIG_NAME" \
  username="$APP_USER" \
  rotation_period="$ROTATION_PERIOD"

echo "Done. ESO reads static creds via ClusterSecretStore vault-hermes-dev-database:"
echo "  path: database/static-creds/${STATIC_ROLE}"
echo "Next: deploy dev-dynamic ExternalSecrets (make eso-sync-dynamic or skaffold -p dynamic-secrets)"
