#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../" && pwd)"
cd "$PROJECT_ROOT"

echo "=========================================="
echo "HARD RESET"
echo "=========================================="

echo "1. Fixing file permissions..."
sudo chown -R $(whoami):$(whoami) .

echo "2. Stopping Skaffold..."
skaffold delete || true

echo "3. Deleting all PVCs (Database Volumes)..."
kubectl delete pvc --all -n hermes-dev || true

echo "4. Pruning Docker volumes..."
docker volume prune -f

echo "5. Wiping all old secrets.env files..."
find . -name "secrets.env" -type f -delete
