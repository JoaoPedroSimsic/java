#!/usr/bin/env bash
# Teardown only — remove ESO custom resources while the validating webhook is still reachable.
# Run via `make teardown`. Do NOT wire this into Skaffold deploy hooks.
set -euo pipefail

echo "Deleting ExternalSecret resources in hermes-dev..."
kubectl delete externalsecret --all -n hermes-dev --ignore-not-found --wait=false 2>/dev/null || true

echo "Deleting ClusterSecretStore vault-hermes-dev..."
kubectl delete clustersecretstore vault-hermes-dev --ignore-not-found --wait=false 2>/dev/null || true

echo "ESO custom resources removed (or already absent)."
