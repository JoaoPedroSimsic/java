#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export MANIFESTS_OVERLAY="${MANIFESTS_OVERLAY:-$(cd "$SCRIPT_DIR/../manifests/dev-dynamic" && pwd)}"
bash "$SCRIPT_DIR/deploy-dev.sh"
