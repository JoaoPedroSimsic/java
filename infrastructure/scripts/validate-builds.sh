#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CONTAINER_CMD="${CONTAINER_CMD:-$REPO_ROOT/scripts/podman-minikube-build.sh}"
SERVICES_JSON="${REPO_ROOT}/.github/services.json"

bash "$REPO_ROOT/scripts/setup-podman.sh"

if [[ ! -f "$SERVICES_JSON" ]]; then
  echo "Missing $SERVICES_JSON"
  exit 1
fi

FAILED=0
while IFS= read -r line; do
  svc="$(echo "$line" | python3 -c "import sys,json; print(json.load(sys.stdin)['service'])")"
  path="$(echo "$line" | python3 -c "import sys,json; print(json.load(sys.stdin)['path'])")"
  ctx="$(echo "$line" | python3 -c "import sys,json; print(json.load(sys.stdin)['context'])")"

  echo "--- Building $svc from $path/Dockerfile ---"
  if $CONTAINER_CMD build --network=host \
    -t "hermes-${svc}:validate" \
    -f "${REPO_ROOT}/${path}/Dockerfile" "${REPO_ROOT}/${ctx}"; then
    echo "OK: $svc"
  else
    echo "FAIL: $svc"
    FAILED=$((FAILED + 1))
  fi
done < <(python3 -c "import json; print('\n'.join(json.dumps(e) for e in json.load(open('$SERVICES_JSON'))))")

if [[ $FAILED -gt 0 ]]; then
  echo "$FAILED service(s) failed to build"
  exit 1
fi
echo "All service images built successfully with $CONTAINER_CMD"
