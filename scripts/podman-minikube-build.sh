#!/usr/bin/env bash
# Podman build wrapper for minikube: uses podman-env when available, otherwise
# builds on the host and loads the image into the minikube node via cri-dockerd.
set -euo pipefail

PROFILE="${MINIKUBE_PROFILE:-hermes-dev}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Only intercept build commands; pass everything else through to podman.
if [[ "${1:-}" != "build" ]]; then
  exec podman "$@"
fi

tag=""
prev=""
for arg in "$@"; do
  if [[ "$prev" == "-t" ]]; then
    tag="$arg"
  fi
  prev="$arg"
done

load_image_into_minikube() {
  local image_tag="$1"
  if [[ -z "$image_tag" ]]; then
    return 0
  fi

  if ! podman ps --format '{{.Names}}' 2>/dev/null | grep -qx "$PROFILE"; then
    echo "WARNING: minikube node container '$PROFILE' is not running; skipping image load for $image_tag"
    return 0
  fi

  # Host podman stores short names as localhost/<name>:<tag>.
  if podman image exists "localhost/$image_tag" 2>/dev/null && ! podman image exists "$image_tag" 2>/dev/null; then
    podman tag "localhost/$image_tag" "$image_tag"
  fi

  echo "Loading $image_tag into minikube profile $PROFILE (cri-dockerd)..."
  podman save "$image_tag" | podman exec -i "$PROFILE" docker load

  # Kubelet requests auth-service:tag; cri-dockerd needs that exact reference.
  if podman exec "$PROFILE" docker image inspect "localhost/$image_tag" >/dev/null 2>&1; then
    podman exec "$PROFILE" docker tag "localhost/$image_tag" "$image_tag"
  fi
}

use_image_load=false
eval "$(minikube -p "$PROFILE" podman-env -u 2>/dev/null)" || true
if podman_env="$(minikube -p "$PROFILE" podman-env 2>/dev/null)"; then
  eval "$podman_env"
  if ! podman info >/dev/null 2>&1; then
    use_image_load=true
  fi
else
  use_image_load=true
fi

if [[ "$use_image_load" == "true" ]]; then
  eval "$(minikube -p "$PROFILE" podman-env -u 2>/dev/null)" || true
  bash "$REPO_ROOT/scripts/setup-podman.sh"
  podman "$@"
  load_image_into_minikube "$tag"
else
  podman "$@"
fi
