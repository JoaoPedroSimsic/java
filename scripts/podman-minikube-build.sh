#!/usr/bin/env bash
# Podman build wrapper for minikube: builds images and loads them directly
# into the minikube node's containerd via podman exec + ctr.
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

  local src_ref="$image_tag"
  if podman image exists "localhost/$image_tag" 2>/dev/null; then
    podman tag "localhost/$image_tag" "$image_tag" 2>/dev/null
  elif ! podman image exists "$image_tag" 2>/dev/null; then
    echo "ERROR: image $image_tag not found in podman"
    return 1
  fi

  echo "Loading $image_tag into minikube profile $PROFILE..."
  local tmp_tar
  tmp_tar="$(mktemp /tmp/hermes-image-XXXXXX.tar)"
  podman save "$image_tag" -o "$tmp_tar"
  podman cp "$tmp_tar" "$PROFILE":/tmp/hermes-image.tar
  podman exec "$PROFILE" ctr -n k8s.io image import /tmp/hermes-image.tar
  podman exec "$PROFILE" ctr -n k8s.io image tag "$image_tag" "docker.io/library/$image_tag" 2>/dev/null || true
  podman exec "$PROFILE" rm /tmp/hermes-image.tar
  rm -f "$tmp_tar"
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
