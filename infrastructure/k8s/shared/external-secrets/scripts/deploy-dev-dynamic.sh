#!/usr/bin/env bash
# Deploy dev ESO with Vault database static-role ExternalSecrets (Phase D opt-in).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export MANIFESTS_OVERLAY="${MANIFESTS_OVERLAY:-$(cd "$SCRIPT_DIR/../manifests/dev-dynamic" && pwd)}"
bash "$SCRIPT_DIR/deploy-dev.sh"
