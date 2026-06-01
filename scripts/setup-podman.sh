#!/usr/bin/env bash
set -euo pipefail

CONFIG_DIR="${XDG_CONFIG_HOME:-$HOME/.config}/containers/registries.conf.d"
CONF_FILE="$CONFIG_DIR/001-hermes-unqualified-search.conf"

mkdir -p "$CONFIG_DIR"

if [[ -f "$CONF_FILE" ]] && grep -q 'unqualified-search-registries.*docker.io' "$CONF_FILE" 2>/dev/null; then
  exit 0
fi

cat >"$CONF_FILE" <<'EOF'
unqualified-search-registries = ["docker.io"]
EOF

echo "Configured Podman unqualified-search-registries (docker.io) in $CONF_FILE"
