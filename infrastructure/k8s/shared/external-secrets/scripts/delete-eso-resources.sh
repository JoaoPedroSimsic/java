#!/usr/bin/env bash
set -euo pipefail

echo "Deleting ExternalSecret resources in hermes-dev..."
kubectl delete externalsecret --all -n hermes-dev --ignore-not-found --wait=false 2>/dev/null || true

echo "Deleting ClusterSecretStore vault-hermes-dev..."
kubectl delete clustersecretstore vault-hermes-dev --ignore-not-found --wait=false 2>/dev/null || true

echo "ESO custom resources removed (or already absent)."
