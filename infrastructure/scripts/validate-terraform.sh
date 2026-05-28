#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TF_MODULES=(
  "infrastructure/terraform/secrets-manager"
  "infrastructure/terraform/cognito"
)
FAILED=0

package_github_auth_lambda() {
  local lambda_dir="$REPO_ROOT/infrastructure/lambda/github-auth"
  local zip="$lambda_dir/function.zip"

  if [[ -f "$zip" ]] && [[ "$lambda_dir/src/index.ts" -ot "$zip" ]]; then
    return 0
  fi

  echo "Packaging github-auth lambda for terraform validate..."
  if command -v bun >/dev/null 2>&1; then
    (cd "$lambda_dir" && bun run package)
  elif [[ -f "$lambda_dir/dist/index.js" ]]; then
    (cd "$lambda_dir/dist" && zip -qr ../function.zip .)
  else
    echo "FAIL: cannot package github-auth lambda (install bun or build dist/)"
    return 1
  fi
}

for mod in "${TF_MODULES[@]}"; do
  if [[ ! -d "$REPO_ROOT/$mod" ]]; then
    echo "SKIP: $mod not found"
    continue
  fi

  if [[ "$mod" == "infrastructure/terraform/cognito" ]]; then
    package_github_auth_lambda || { FAILED=$((FAILED + 1)); continue; }
  fi

  echo "--- Validating $mod ---"
  (
    cd "$REPO_ROOT/$mod"
    terraform fmt -check -recursive
    terraform init -backend=false -input=false > /dev/null 2>&1
    terraform validate
  ) && echo "OK: $mod" || { echo "FAIL: $mod"; FAILED=$((FAILED + 1)); }
done

if [[ $FAILED -gt 0 ]]; then
  echo "$FAILED Terraform module(s) failed"
  exit 1
fi
echo "All Terraform modules valid"
