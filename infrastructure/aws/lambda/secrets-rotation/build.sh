#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT="${SCRIPT_DIR}/function.zip"

rm -f "$OUT"
(cd "$SCRIPT_DIR" && zip -q -j "$OUT" handler.py)
echo "Built $OUT"
