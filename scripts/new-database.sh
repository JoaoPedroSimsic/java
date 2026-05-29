#!/usr/bin/env bash
# Generate Postgres (or Redis) K8s manifests, ExternalSecrets, and Vault seed entries.
#
# Usage: scripts/new-database.sh <short-name> [--type postgres|redis]
#
# Example: scripts/new-database.sh chat --type postgres
#   Creates infrastructure/k8s/shared/postgres/chat-db/ ...
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
# shellcheck source=scripts/lib/render-template.sh
source "$SCRIPT_DIR/lib/render-template.sh"

TEMPLATES="$REPO_ROOT/infrastructure/templates"
SHORT_NAME=""
DB_TYPE="postgres"

usage() {
  echo "Usage: $0 <short-name> [--type postgres|redis]"
  echo "  short-name: service prefix without -service (e.g. chat for chat-service)"
  exit 1
}

[[ $# -ge 1 ]] || usage
SHORT_NAME="${1%-service}"
shift

while [[ $# -gt 0 ]]; do
  case "$1" in
    --type)
      DB_TYPE="${2:?}"
      shift 2
      ;;
    -h|--help) usage ;;
    *) echo "Unknown option: $1"; usage ;;
  esac
done

case "$DB_TYPE" in
  postgres|redis) ;;
  *) echo "Unsupported type: $DB_TYPE (use postgres or redis)"; exit 1 ;;
esac

DB_SVC="${SHORT_NAME}-db"
DB_DEPLOY="${SHORT_NAME}-postgres"
PG_SECRETS="${SHORT_NAME}-postgres-secrets"
REDIS_DEPLOY="${SHORT_NAME}-redis"
REDIS_SVC="${SHORT_NAME}-cache"
VAULT_PATH_PREFIX="hermes"

secret_store_for_env() {
  case "$1" in
    dev) echo "vault-hermes-dev" ;;
    staging) echo "aws-hermes-staging" ;;
    prod) echo "aws-hermes-prod" ;;
    *) echo "vault-hermes-dev" ;;
  esac
}

render_tree() {
  local template_dir="$1"
  local dest_dir="$2"
  shift 2
  local args=("$@")

  mkdir -p "$dest_dir"
  local f dest
  for f in "$template_dir"/*; do
    [[ -f "$f" ]] || continue
    dest="$dest_dir/$(basename "$f")"
    render_template "$f" "$dest" "${args[@]}"
  done
}

if [[ "$DB_TYPE" == "postgres" ]]; then
  PG_BASE="$REPO_ROOT/infrastructure/k8s/shared/postgres/${SHORT_NAME}-db"
  if [[ -d "$PG_BASE/base" ]]; then
    echo "Postgres manifests already exist: $PG_BASE"
    exit 1
  fi

  echo "Creating Postgres manifests at $PG_BASE ..."
  render_tree "$TEMPLATES/postgres" "$PG_BASE/base" \
    DB_SVC="$DB_SVC" DB_DEPLOY="$DB_DEPLOY" PG_SECRETS="$PG_SECRETS"

  for env in dev staging prod; do
    overlay="$PG_BASE/overlays/$env"
    render_tree "$TEMPLATES/postgres-overlay" "$overlay" \
      DB_DEPLOY="$DB_DEPLOY" ENVIRONMENT="$env"
  done

  dev_local="$PG_BASE/overlays/dev-local"
  mkdir -p "$dev_local"
  cat >"$dev_local/kustomization.yaml" <<EOF
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources:
- ../dev
secretGenerator:
- name: ${PG_SECRETS}
  envs:
  - secrets.env
labels:
- pairs:
    environment: dev
    secret-source: local
  includeSelectors: true
EOF

  for env in dev staging prod; do
    store="$(secret_store_for_env "$env")"
    manifest_dir="$REPO_ROOT/infrastructure/k8s/shared/external-secrets/manifests/$env"
    mkdir -p "$manifest_dir"

    render_template "$TEMPLATES/external-secret-postgres.yaml" \
      "$manifest_dir/${SHORT_NAME}-postgres-secrets.yaml" \
      PG_SECRETS="$PG_SECRETS" \
      SECRET_STORE="$store" \
      VAULT_PATH="${VAULT_PATH_PREFIX}/${env}/services/${DB_SVC}/postgres"

    kust="$manifest_dir/kustomization.yaml"
    append_unique_line "$kust" "  - ${SHORT_NAME}-postgres-secrets.yaml"
  done

  VAULT_SECRET_FILE="$REPO_ROOT/infrastructure/vault/secrets/${SHORT_NAME}-db-postgres.env"
  if [[ ! -f "$VAULT_SECRET_FILE" ]]; then
    cat >"$VAULT_SECRET_FILE" <<EOF
# vault_path=services/${DB_SVC}/postgres
POSTGRES_USER=\$POSTGRES_USER
POSTGRES_PASSWORD=\$POSTGRES_PASSWORD
POSTGRES_DB=\$POSTGRES_DB
APP_USER=\$APP_USER
APP_PASSWORD=\$APP_PASSWORD
FLYWAY_USER=\$FLYWAY_USER
FLYWAY_PASSWORD=\$FLYWAY_PASSWORD
EOF
    echo "Created Vault secret definition: $VAULT_SECRET_FILE"
  else
    echo "Vault secret definition already exists: $VAULT_SECRET_FILE"
  fi

  echo "Postgres database scaffolding complete."
fi

if [[ "$DB_TYPE" == "redis" ]]; then
  REDIS_BASE="$REPO_ROOT/infrastructure/k8s/shared/redis/${SHORT_NAME}-redis"
  if [[ -d "$REDIS_BASE/base" ]]; then
    echo "Redis manifests already exist: $REDIS_BASE"
    exit 1
  fi

  echo "Creating Redis manifests at $REDIS_BASE ..."
  render_tree "$TEMPLATES/redis" "$REDIS_BASE/base" \
    REDIS_DEPLOY="$REDIS_DEPLOY" REDIS_SVC="$REDIS_SVC"

  for env in dev staging prod; do
    overlay="$REDIS_BASE/overlays/$env"
    render_tree "$TEMPLATES/redis-overlay" "$overlay" \
      REDIS_DEPLOY="$REDIS_DEPLOY" ENVIRONMENT="$env"
  done

  echo "Redis cache scaffolding complete."
fi
