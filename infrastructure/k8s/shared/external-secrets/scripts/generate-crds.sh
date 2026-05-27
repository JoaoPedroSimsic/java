#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CRDS_DIR="$(cd "$SCRIPT_DIR/../crds" && pwd)"
BUNDLE="$CRDS_DIR/bundle.yaml"
CHART_VERSION="0.10.5"

filter_crds() {
  if command -v yq >/dev/null 2>&1; then
    yq eval-all 'select(.kind == "CustomResourceDefinition")' -
    return
  fi
  if python3 -c "import yaml" 2>/dev/null; then
    python3 -c '
import sys, yaml
first = True
for doc in yaml.safe_load_all(sys.stdin):
    if isinstance(doc, dict) and doc.get("kind") == "CustomResourceDefinition":
        if not first:
            print("---")
        yaml.dump(doc, sys.stdout, default_flow_style=False)
        first = False
'
    return
  fi
  echo "Need yq (https://github.com/mikefarah/yq) or python3 with PyYAML to filter CRDs." >&2
  exit 1
}

helm repo add external-secrets https://charts.external-secrets.io 2>/dev/null || true
helm repo update external-secrets

helm template external-secrets external-secrets/external-secrets \
  --version "$CHART_VERSION" \
  --include-crds \
  --no-hooks \
  | filter_crds > "$BUNDLE"

echo "Wrote $BUNDLE"
