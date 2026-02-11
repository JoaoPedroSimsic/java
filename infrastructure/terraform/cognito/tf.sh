#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../../.." && pwd)"
LAMBDA_DIR="$SCRIPT_DIR/../../lambda/github-auth"

if [ -f "$ROOT_DIR/.env" ]; then
  set -a
  source "$ROOT_DIR/.env"
  set +a
fi

export TF_VAR_aws_region="$AWS_DEFAULT_REGION"
export TF_VAR_github_client_id="$GITHUB_CLIENT_ID"
export TF_VAR_github_client_secret="$GITHUB_CLIENT_SECRET"
export TF_VAR_frontend_url="${FRONTEND_URL:-http://localhost:3000}"

cd "$SCRIPT_DIR"

# Build Lambda if function.zip doesn't exist or source changed
if [ ! -f "$LAMBDA_DIR/function.zip" ] || [ "$LAMBDA_DIR/src/index.ts" -nt "$LAMBDA_DIR/function.zip" ]; then
  echo "Building Lambda function..."
  cd "$LAMBDA_DIR"
  npm install
  npm run build
  cd dist
  cp ../package.json .
  cp ../package-lock.json .
  npm install --omit=dev
  zip -r ../function.zip .
  cd "$SCRIPT_DIR"
  echo "Lambda build complete."
fi

if [ ! -d ".terraform" ]; then
  terraform init
fi

terraform "$@"
