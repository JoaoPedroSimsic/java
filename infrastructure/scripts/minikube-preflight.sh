#!/usr/bin/env bash
# Verify a minikube profile has working pod networking (ClusterIP / DNS).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=infrastructure/scripts/minikube-common.sh
source "$SCRIPT_DIR/minikube-common.sh"

PROFILE="${MINIKUBE_PROFILE:-hermes-dev}"

unset KUBECONFIG
if ! minikube_profile_running "$PROFILE"; then
  echo "ERROR: minikube profile '$PROFILE' is not running or API is not ready."
  echo "Start it with: make minikube-up MINIKUBE_PROFILE=$PROFILE"
  exit 1
fi

kubectl config use-context "$PROFILE" >/dev/null

echo "Checking cluster DNS (kube-dns ClusterIP) on profile $PROFILE..."
for attempt in $(seq 1 30); do
  if kubectl run "dns-preflight-$$" --rm -i --restart=Never \
    --image=busybox:1.36 \
    --command -- nslookup kubernetes.default.svc.cluster.local >/dev/null 2>&1; then
    echo "OK: cluster DNS works"
    exit 0
  fi
  echo "  DNS not ready yet (attempt $attempt/30)..."
  sleep 4
done

cat <<EOF
ERROR: cluster DNS is not working on minikube profile '$PROFILE'.

This blocks service discovery (auth-db, rabbitmq, etc.) and is common with
rootless Podman + minikube when kube-proxy cannot program iptables.

Try (requires sudo once):
  sudo modprobe br_netfilter
  sudo sysctl -w net.bridge.bridge-nf-call-iptables=1
  sudo sysctl -w net.bridge.bridge-nf-call-ip6tables=1
  make minikube-reset MINIKUBE_PROFILE=$PROFILE

Also ensure slirp4netns is installed for rootless networking.
See podman.md "Minikube rootless troubleshooting".
EOF
exit 1
