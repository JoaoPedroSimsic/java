#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../../.." && pwd)"

if [ -f "$ROOT_DIR/.env" ]; then
  export $(grep -v '^#' "$ROOT_DIR/.env" | grep -v '^$' | xargs)
fi

export TF_VAR_aws_region="${AWS_REGION:-us-east-1}"
export TF_VAR_github_client_id="$GITHUB_CLIENT_ID"
export TF_VAR_github_client_secret="$GITHUB_CLIENT_SECRET"

cd "$SCRIPT_DIR"

terraform init
terraform "$@"
