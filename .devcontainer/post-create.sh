#!/usr/bin/env bash
set -euo pipefail

if [ -f "frontend/package.json" ]; then
  cd frontend
  if command -v bun >/dev/null 2>&1; then
    bun install
  else
    npm install
  fi
  cd ..
fi

if [ -f "mvnw" ]; then
  chmod +x mvnw || true
fi

echo "Devcontainer setup complete."
