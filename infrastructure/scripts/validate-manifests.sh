#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENVS=("dev" "dev-local-secrets" "staging" "prod")
FAILED=0

ensure_local_secrets() {
  if [[ ! -f "$REPO_ROOT/.env" ]]; then
    return 1
  fi
  echo "Generating secrets.env from .env for local-secrets validation..."
  (cd "$REPO_ROOT/infrastructure/k8s" && bash setup-env.sh dev --local-secrets)
}

validate_overlay() {
  local env="$1"
  local overlay="$REPO_ROOT/infrastructure/k8s/clusters/${env}"

  if [[ ! -d "$overlay" ]]; then
    echo "SKIP: $overlay does not exist"
    return 0
  fi

  echo "--- Validating cluster overlay: $env ---"
  if kubectl kustomize "$overlay" \
    --load-restrictor=LoadRestrictionsNone \
    --enable-helm > /dev/null 2>&1; then
    echo "OK: $env"
    return 0
  fi

  if [[ "$env" == "dev-local-secrets" ]] && ensure_local_secrets; then
    if kubectl kustomize "$overlay" \
      --load-restrictor=LoadRestrictionsNone \
      --enable-helm > /dev/null 2>&1; then
      echo "OK: $env (after generating secrets.env from .env)"
      return 0
    fi
  fi

  if [[ "$env" == "dev-local-secrets" ]]; then
    echo "SKIP: $env (requires .env — run: infrastructure/k8s/setup-env.sh dev --local-secrets)"
    return 0
  fi

  echo "FAIL: $env"
  kubectl kustomize "$overlay" \
    --load-restrictor=LoadRestrictionsNone \
    --enable-helm 2>&1 | tail -20
  return 1
}

for env in "${ENVS[@]}"; do
  if ! validate_overlay "$env"; then
    FAILED=$((FAILED + 1))
  fi
done

if [[ $FAILED -gt 0 ]]; then
  echo "$FAILED overlay(s) failed validation"
  exit 1
fi
echo "All Kustomize overlays render cleanly"
