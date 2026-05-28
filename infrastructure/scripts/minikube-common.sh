#!/usr/bin/env bash
# Shared helpers for rootless Podman + minikube (minikube status needs sudo).
set -euo pipefail

minikube_profile_running() {
  local profile="${1:?profile required}"

  if podman ps --format '{{.Names}}' 2>/dev/null | grep -qx "$profile"; then
    return 0
  fi

  if kubectl config get-contexts -o name 2>/dev/null | grep -qx "$profile"; then
    kubectl --context="$profile" get nodes --no-headers 2>/dev/null | grep -q ' Ready'
    return $?
  fi

  return 1
}

wait_for_minikube_profile() {
  local profile="${1:?profile required}"
  local attempts="${2:-60}"

  local i
  for ((i = 1; i <= attempts; i++)); do
    if minikube_profile_running "$profile"; then
      return 0
    fi
    sleep 2
  done
  return 1
}
