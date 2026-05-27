#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TERRAFORM_DIR="$SCRIPT_DIR"

if [[ ! -d "$TERRAFORM_DIR/.terraform" ]]; then
  terraform -chdir="$TERRAFORM_DIR" init
fi

terraform -chdir="$TERRAFORM_DIR" "$@"
