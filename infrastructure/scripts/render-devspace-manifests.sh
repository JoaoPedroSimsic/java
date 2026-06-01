#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT="$REPO_ROOT/infrastructure/k8s/_rendered"

mkdir -p "$OUT"

echo "Rendering infrastructure/k8s/clusters/dev..."
kubectl kustomize "$REPO_ROOT/infrastructure/k8s/clusters/dev" \
  --load-restrictor=LoadRestrictionsNone \
  > "$OUT/cluster-dev.yaml"

echo "Rendering infrastructure/k8s/shared/external-secrets/manifests/dev..."
kubectl kustomize "$REPO_ROOT/infrastructure/k8s/shared/external-secrets/manifests/dev" \
  --load-restrictor=LoadRestrictionsNone \
  > "$OUT/eso-secrets-dev.yaml"

echo "Rendering infrastructure/k8s/shared/external-secrets/manifests/dev-dynamic..."
kubectl kustomize "$REPO_ROOT/infrastructure/k8s/shared/external-secrets/manifests/dev-dynamic" \
  --load-restrictor=LoadRestrictionsNone \
  > "$OUT/eso-secrets-dev-dynamic.yaml"

echo "Manifests rendered to $OUT/"
