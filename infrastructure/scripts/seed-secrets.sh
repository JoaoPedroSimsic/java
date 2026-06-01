#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
ENV="${HERMES_ENV:-prod}"

if [[ ! "$ENV" =~ ^(staging|prod)$ ]]; then
  echo "HERMES_ENV must be staging or prod (got: $ENV)"
  exit 1
fi

ENV_FILE="$REPO_ROOT/.env"
[[ -f "$REPO_ROOT/.env.$ENV" ]] && ENV_FILE="$REPO_ROOT/.env.$ENV"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Environment file not found: $ENV_FILE"
  echo "Copy .env.example to .env.$ENV and fill in values."
  exit 1
fi

set -a
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

if [[ -n "${COGNITO_CLIENT_SECRET:-}" ]]; then
  require_var COGNITO_CLIENT_SECRET
fi

AWS_REGION="${AWS_DEFAULT_REGION:-${AWS_REGION:-sa-east-1}}"

aws_sm_put_json() {
  local name="$1"
  local json="$2"
  echo "Writing secret $name ..."
  if aws secretsmanager describe-secret --secret-id "$name" --region "$AWS_REGION" >/dev/null 2>&1; then
    aws secretsmanager put-secret-value \
      --secret-id "$name" \
      --secret-string "$json" \
      --region "$AWS_REGION" >/dev/null
  else
    echo "Secret $name does not exist. Run terraform apply in infrastructure/terraform/secrets-manager first."
    exit 1
  fi
}

PREFIX="hermes/$ENV"

aws_sm_put_json "$PREFIX/shared/jwt-signing-key" \
  "$(jq -nc --arg v "$GATEWAY_SECRET" '{value: $v}')"

aws_sm_put_json "$PREFIX/shared/rabbitmq" \
  "$(jq -nc --arg u "$RABBITMQ_USERNAME" --arg p "$RABBITMQ_PASSWORD" '{username: $u, password: $p}')"

aws_sm_put_json "$PREFIX/shared/github-oauth" \
  "$(jq -nc --arg id "$GITHUB_CLIENT_ID" --arg secret "$GITHUB_CLIENT_SECRET" '{client_id: $id, client_secret: $secret}')"

postgres_json() {
  jq -nc \
    --arg pu "$POSTGRES_USER" \
    --arg pp "$POSTGRES_PASSWORD" \
    --arg db "$POSTGRES_DB" \
    --arg au "$APP_USER" \
    --arg ap "$APP_PASSWORD" \
    --arg fu "$FLYWAY_USER" \
    --arg fp "$FLYWAY_PASSWORD" \
    '{
      POSTGRES_USER: $pu,
      POSTGRES_PASSWORD: $pp,
      POSTGRES_DB: $db,
      APP_USER: $au,
      APP_PASSWORD: $ap,
      FLYWAY_USER: $fu,
      FLYWAY_PASSWORD: $fp
    }'
}

aws_sm_put_json "$PREFIX/services/auth-db/postgres" "$(postgres_json)"
aws_sm_put_json "$PREFIX/services/user-db/postgres" "$(postgres_json)"

aws_sm_put_json "$PREFIX/services/keycloak/keycloak-admin" \
  "$(jq -nc --arg u "$KEYCLOAK_ADMIN" --arg p "$KEYCLOAK_ADMIN_PASSWORD" '{username: $u, password: $p}')"

if [[ -n "${COGNITO_CLIENT_SECRET:-}" ]]; then
  aws_sm_put_json "$PREFIX/services/auth-service/cognito" \
    "$(jq -nc --arg s "$COGNITO_CLIENT_SECRET" '{client_secret: $s}')"
fi

echo "Done. AWS Secrets Manager paths seeded under $PREFIX/"
echo "Next: HERMES_ENV=$ENV bash infrastructure/k8s/shared/external-secrets/scripts/deploy-aws.sh"
