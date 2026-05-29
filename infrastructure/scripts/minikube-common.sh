#!/usr/bin/env bash
# Shared helpers for rootless Podman + minikube (minikube status needs sudo).
set -euo pipefail

# Ensure br_netfilter is loaded so that bridge-forwarded pod traffic passes
# through iptables/CONNTRACK. Without it, Kubernetes service ClusterIP routing
# breaks silently under rootless Podman after a minikube restart.
ensure_br_netfilter() {
  local current
  current="$(cat /proc/sys/net/bridge/bridge-nf-call-iptables 2>/dev/null || echo '')"
  if [[ "$current" == "1" ]]; then
    return 0
  fi

  echo "br_netfilter not active — loading for rootless Podman + minikube CNI..."
  if ! sudo modprobe br_netfilter 2>/dev/null; then
    echo "WARNING: 'sudo modprobe br_netfilter' failed." \
         "Cluster DNS / service routing may not work."
    return 0
  fi
  sudo sysctl -qw net.bridge.bridge-nf-call-iptables=1 2>/dev/null || true
  sudo sysctl -qw net.bridge.bridge-nf-call-ip6tables=1  2>/dev/null || true
  echo "br_netfilter loaded OK."
}

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
