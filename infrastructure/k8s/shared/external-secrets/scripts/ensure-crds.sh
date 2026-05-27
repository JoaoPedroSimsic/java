#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CRDS_DIR="$(cd "$SCRIPT_DIR/../crds" && pwd)"
BUNDLE="$CRDS_DIR/bundle.yaml"

if [[ ! -f "$BUNDLE" ]]; then
  echo "CRD bundle missing; generating..."
  bash "$SCRIPT_DIR/generate-crds.sh"
fi

REQUIRED_CRDS=(
  clustersecretstores.external-secrets.io
  externalsecrets.external-secrets.io
)

need_install=false
for crd in "${REQUIRED_CRDS[@]}"; do
  if ! kubectl get crd "$crd" >/dev/null 2>&1; then
    need_install=true
    break
  fi
done

if $need_install; then
  echo "Installing External Secrets Operator CRDs..."
  kubectl apply --server-side --force-conflicts -k "$CRDS_DIR"
fi

for crd in "${REQUIRED_CRDS[@]}"; do
  kubectl wait --for=condition=Established "crd/$crd" --timeout=180s
done

echo "External Secrets Operator CRDs are ready."
