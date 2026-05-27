#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

ENV=${1:-dev}
ALLOWED_ENVS=("dev" "staging" "prod")

if [[ ! " ${ALLOWED_ENVS[@]} " =~ " ${ENV} " ]]; then
    echo -e "${RED}Error: Invalid environment '$ENV'. Use dev, staging, or prod.${NC}"
    exit 1
fi

echo "=========================================="
echo -e "Hermes K8s Setup: ${BLUE}${ENV^^}${NC}"
echo "=========================================="

ENV_FILE="../../.env"

[ -f "../../.env.${ENV}" ] && ENV_FILE="../../.env.${ENV}"

if [ -f "$ENV_FILE" ]; then
    echo -e "${BLUE}Loading variables from $ENV_FILE...${NC}"
    set -a
    source "$ENV_FILE"
    set +a
else
    echo -e "${RED}Error: Environment file $ENV_FILE not found.${NC}"
    exit 1
fi

generate_files() {
    local target_dir=$1
    shift
    local vars=("$@")
    
    mkdir -p "$target_dir" || { echo -e "${RED}Error: Cannot create directory $target_dir${NC}"; exit 1; }
    
    > "$target_dir/secrets.env"
    > "$target_dir/params.env"
    
    echo -ne "${YELLOW}Processing $(basename "$(dirname "$(dirname "$target_dir")")")... ${NC}"

    for var in "${vars[@]}"; do
        if [[ -z "${!var:-}" ]]; then
            echo -e "\n${RED}FAILED: Variable '$var' is missing or empty in $ENV_FILE${NC}"
            exit 1
        fi

        if [[ "$var" =~ (^USERNAME|_USER|PASSWORD|PASS|SECRET|KEY|TOKEN) ]]; then
            echo "$var=${!var}" >> "$target_dir/secrets.env"
        else
            echo "$var=${!var}" >> "$target_dir/params.env"
        fi
    done

    if [[ -f "$target_dir/secrets.env" && -f "$target_dir/params.env" ]]; then
        echo -e "${GREEN}✓${NC}"
    else
        echo -e "${RED}FAILED: Files were not generated in $target_dir${NC}"
        exit 1
    fi
}

echo -e "Validating and generating manifests..."

HTTP_GATEWAY_VARS=(
    "GATEWAY_PROFILE" "HTTP_GATEWAY_PORT" "GATEWAY_SECRET" "GATEWAY_CACHE_HOST" 
    "REDIS_PORT" "RATE_LIMIT_AUTHENTICATED" "RATE_LIMIT_AUTHENTICATED_BURST"
    "RATE_LIMIT_UNAUTHENTICATED" "RATE_LIMIT_UNAUTHENTICATED_BURST" "CACHE_TTL_SECONDS"
    "AUTH_SERVICE_HOST" "AUTH_SERVICE_PORT" "USER_SERVICE_HOST" "USER_SERVICE_PORT"
    "CB_SLIDING_WINDOW_SIZE" "CB_MINIMUM_NUMBER_OF_CALLS" "CB_FAILURE_RATE_THRESHOLD"
    "CB_WAIT_DURATION_OPEN_STATE_SECONDS" "CB_PERMITTED_CALLS_HALF_OPEN"
    "TIME_LIMITER_TIMEOUT_SECONDS"
)
HTTP_GATEWAY_DEV_VARS=("KC_JWKS_URL" "KC_JWT_ISSUER")
HTTP_GATEWAY_PROD_VARS=("COGNITO_JWKS_URL" "COGNITO_JWT_ISSUER" "CORS_ALLOWED_ORIGINS")

if [[ "$ENV" == "dev" ]]; then
    generate_files "gateways/http-gateway/overlays/$ENV" "${HTTP_GATEWAY_VARS[@]}" "${HTTP_GATEWAY_DEV_VARS[@]}"
elif [[ "$ENV" == "prod" ]]; then
    generate_files "gateways/http-gateway/overlays/$ENV" "${HTTP_GATEWAY_VARS[@]}" "${HTTP_GATEWAY_PROD_VARS[@]}"
else
    generate_files "gateways/http-gateway/overlays/$ENV" "${HTTP_GATEWAY_VARS[@]}"
fi

AUTH_VARS=(
    "AUTH_PROFILE" "AUTH_SERVICE_HOST" "AUTH_SERVICE_PORT" "AUTH_COOKIE_DOMAIN"
    "AUTH_COOKIE_SECURE" "AUTH_COOKIE_SAME_SITE" "GITHUB_REDIRECT_URI" 
    "AUTH_DB_HOST" "POSTGRES_PORT" "POSTGRES_DB"
    "AUTH_CACHE_HOST" "REDIS_PORT" "AUTH_ACCESS_TOKEN_MAX_AGE"
    "AUTH_REFRESH_TOKEN_MAX_AGE" "APP_USER" "APP_PASSWORD" "FLYWAY_USER" "FLYWAY_PASSWORD"
    "RABBITMQ_HOST" "RABBITMQ_PORT" "RABBITMQ_USERNAME" "RABBITMQ_PASSWORD" 
    "OUTBOX_BATCH_SIZE" "OUTBOX_MAX_ATTEMPTS" "OUTBOX_POLL_INTERVAL"
    "GITHUB_CLIENT_ID" "GITHUB_CLIENT_SECRET"
)
AUTH_DEV_VARS=("KC_SERVER_URL" "KC_EXTERNAL_URL" "KEYCLOAK_REALM" "KEYCLOAK_CLIENT_ID" "KEYCLOAK_ADMIN" "KEYCLOAK_ADMIN_PASSWORD")
AUTH_PROD_VARS=("COGNITO_USER_POOL_ID" "COGNITO_CLIENT_ID" "COGNITO_CLIENT_SECRET" "COGNITO_DOMAIN_URL" "AWS_DEFAULT_REGION")

if [[ "$ENV" == "dev" ]]; then
    generate_files "services/auth-service/overlays/$ENV" "${AUTH_VARS[@]}" "${AUTH_DEV_VARS[@]}"
elif [[ "$ENV" == "prod" ]]; then
    generate_files "services/auth-service/overlays/$ENV" "${AUTH_VARS[@]}" "${AUTH_PROD_VARS[@]}"
else
    generate_files "services/auth-service/overlays/$ENV" "${AUTH_VARS[@]}"
fi

USER_VARS=(
    "USER_PROFILE" "USER_SERVICE_HOST" "USER_SERVICE_PORT" "USER_DB_HOST"
    "POSTGRES_PORT" "POSTGRES_DB" "USER_CACHE_HOST" "REDIS_PORT" 
    "APP_USER" "APP_PASSWORD" "FLYWAY_USER" "FLYWAY_PASSWORD" "GATEWAY_SECRET"
    "RABBITMQ_HOST" "RABBITMQ_PORT" "RABBITMQ_USERNAME" "RABBITMQ_PASSWORD"
)
USER_PROD_VARS=("AWS_DEFAULT_REGION" "AWS_ACCESS_KEY_ID" "AWS_SECRET_ACCESS_KEY")

if [[ "$ENV" == "prod" ]]; then
    generate_files "services/user-service/overlays/$ENV" "${USER_VARS[@]}" "${USER_PROD_VARS[@]}"
else
    generate_files "services/user-service/overlays/$ENV" "${USER_VARS[@]}"
fi

DB_VARS=("POSTGRES_DB" "POSTGRES_USER" "POSTGRES_PASSWORD" "POSTGRES_PORT" "APP_USER" "APP_PASSWORD" "FLYWAY_USER" "FLYWAY_PASSWORD")
generate_files "shared/postgres/auth-db/overlays/$ENV" "${DB_VARS[@]}"
generate_files "shared/postgres/user-db/overlays/$ENV" "${DB_VARS[@]}"

generate_rabbitmq_files() {
    local target_dir="shared/rabbitmq/overlays/$ENV"
    require_mq_var() {
        if [[ -z "${!1:-}" ]]; then
            echo -e "\n${RED}FAILED: Variable '$1' is missing or empty in $ENV_FILE${NC}"
            exit 1
        fi
    }
    require_mq_var RABBITMQ_HOST
    require_mq_var RABBITMQ_PORT
    require_mq_var RABBITMQ_USERNAME
    require_mq_var RABBITMQ_PASSWORD

    mkdir -p "$target_dir"
    > "$target_dir/secrets.env"
    > "$target_dir/params.env"

    echo -ne "${YELLOW}Processing rabbitmq... ${NC}"
    {
        echo "RABBITMQ_HOST=$RABBITMQ_HOST"
        echo "RABBITMQ_PORT=$RABBITMQ_PORT"
    } >> "$target_dir/params.env"
    {
        echo "RABBITMQ_DEFAULT_USER=$RABBITMQ_USERNAME"
        echo "RABBITMQ_DEFAULT_PASS=$RABBITMQ_PASSWORD"
        echo "RABBITMQ_USERNAME=$RABBITMQ_USERNAME"
        echo "RABBITMQ_PASSWORD=$RABBITMQ_PASSWORD"
    } >> "$target_dir/secrets.env"
    echo -e "${GREEN}✓${NC}"
}

generate_rabbitmq_files

KC_VARS=(
    "KEYCLOAK_ADMIN" "KEYCLOAK_ADMIN_PASSWORD" 
    "GITHUB_CLIENT_ID" "GITHUB_CLIENT_SECRET" "POSTGRES_DB"
)
generate_files "shared/keycloak/overlays/$ENV" "${KC_VARS[@]}"

WS_GATEWAY_VARS=(
    "WS_GATEWAY_PORT" "GATEWAY_PROFILE" "NATS_URL" "LOG_LEVEL" "FRONTEND_URL"
    "JWKS_CACHE_TTL" "CHAT_SERVICE_URL" "RATE_LIMIT_AUTHENTICATED" "RATE_LIMIT_AUTHENTICATED_BURST"
    "GATEWAY_CACHE_HOST" "REDIS_PORT"
    "CB_SLIDING_WINDOW_SIZE" "CB_MINIMUM_NUMBER_OF_CALLS" "CB_FAILURE_RATE_THRESHOLD"
    "CB_WAIT_DURATION_OPEN_STATE_SECONDS" "CB_PERMITTED_CALLS_HALF_OPEN"
    "TIME_LIMITER_TIMEOUT_SECONDS" "CONNECTION_MAX_DURATION_MINUTES"
)
WS_GATEWAY_DEV_VARS=("KC_JWKS_URL" "KC_JWT_ISSUER")
WS_GATEWAY_PROD_VARS=("COGNITO_JWKS_URL" "COGNITO_JWT_ISSUER" "CORS_ALLOWED_ORIGINS")

if [[ "$ENV" == "dev" ]]; then
    generate_files "gateways/ws-gateway/overlays/$ENV" "${WS_GATEWAY_VARS[@]}" "${WS_GATEWAY_DEV_VARS[@]}"
elif [[ "$ENV" == "prod" ]]; then
    generate_files "gateways/ws-gateway/overlays/$ENV" "${WS_GATEWAY_VARS[@]}" "${WS_GATEWAY_PROD_VARS[@]}"
else
    generate_files "gateways/ws-gateway/overlays/$ENV" "${WS_GATEWAY_VARS[@]}"
fi

echo -e "\n${GREEN}All files synchronized with .env and validated.${NC}"
