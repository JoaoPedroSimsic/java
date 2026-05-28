#!/usr/bin/env bash
# Configure Podman for Hermes Dockerfiles (short image names like maven:..., golang:...).
# Safe to run repeatedly; writes under $XDG_CONFIG_HOME/containers/registries.conf.d/
set -euo pipefail

CONFIG_DIR="${XDG_CONFIG_HOME:-$HOME/.config}/containers/registries.conf.d"
CONF_FILE="$CONFIG_DIR/001-hermes-unqualified-search.conf"

mkdir -p "$CONFIG_DIR"

if [[ -f "$CONF_FILE" ]] && grep -q 'unqualified-search-registries.*docker.io' "$CONF_FILE" 2>/dev/null; then
  exit 0
fi

cat >"$CONF_FILE" <<'EOF'
# Hermes: allow Dockerfile short names (maven:..., golang:...) to resolve via docker.io
unqualified-search-registries = ["docker.io"]
EOF

echo "Configured Podman unqualified-search-registries (docker.io) in $CONF_FILE"
