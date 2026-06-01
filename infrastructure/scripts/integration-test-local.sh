#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "$REPO_ROOT/infrastructure/scripts/minikube-common.sh"
PROFILE="${MINIKUBE_PROFILE:-hermes-test}"
NAMESPACE="hermes-dev"
TIMEOUT="${SMOKE_TIMEOUT:-600}"
REUSE_CLUSTER="${INTEGRATION_REUSE_CLUSTER:-0}"

unset KUBECONFIG

if [[ ! -f "$REPO_ROOT/.env" ]]; then
  echo "ERROR: $REPO_ROOT/.env is required for the local-secrets integration test."
  echo "Copy .env.example to .env and run: infrastructure/k8s/setup-env.sh dev --local-secrets"
  exit 1
fi

cleanup() {
  [[ "${INTEGRATION_KEEP_CLUSTER:-0}" == "1" ]] && return 0
  echo "Cleaning up test cluster..."
  devspace purge --kube-context "$PROFILE" 2>/dev/null || true
  minikube delete -p "$PROFILE" 2>/dev/null || true
  podman volume rm "$PROFILE" 2>/dev/null || true
}
trap cleanup EXIT

bash "$REPO_ROOT/scripts/setup-podman.sh"

echo "=== Phase 0: Generate local secrets.env overlays ==="
(cd "$REPO_ROOT/infrastructure/k8s" && bash setup-env.sh dev --local-secrets)

echo "=== Phase 1: Cluster bootstrap ==="
if [[ "$REUSE_CLUSTER" == "1" ]] && minikube_profile_running "$PROFILE"; then
  echo "Reusing running minikube profile $PROFILE (INTEGRATION_REUSE_CLUSTER=1)"
else
  minikube delete -p "$PROFILE" 2>/dev/null || true
  podman rm -f "$PROFILE" 2>/dev/null || true
  podman volume rm "$PROFILE" 2>/dev/null || true
  rm -rf "${HOME}/.minikube/profiles/${PROFILE}"
  ROOTLESS_FLAG=""
  if podman info --format '{{.Host.Security.Rootless}}' 2>/dev/null | grep -q true; then
    ROOTLESS_FLAG="--rootless"
  fi
  minikube start -p "$PROFILE" --driver=podman $ROOTLESS_FLAG \
    --kubernetes-version=v1.31.6 \
    --memory=6144 --cpus=3 --wait-timeout=10m \
    --force
  wait_for_minikube_profile "$PROFILE" 60
fi

MINIKUBE_PROFILE="$PROFILE" bash "$REPO_ROOT/infrastructure/scripts/minikube-preflight.sh"

echo "=== Phase 2: Build + deploy (integration profile, no wait hooks) ==="
export MINIKUBE_PROFILE="$PROFILE"
devspace deploy -p integration --force-build --kube-context "$PROFILE"

echo "=== Phase 3: Wait for app deployments (timeout ${TIMEOUT}s each) ==="
for dep in auth-service user-service http-gateway ws-gateway; do
  echo "Waiting for $dep..."
  kubectl --context "$PROFILE" wait deployment/"$dep" -n "$NAMESPACE" \
    --for=condition=Available --timeout="${TIMEOUT}s"
done

echo "=== Phase 4: Health checks ==="
for svc_port in "http-gateway:8081:/actuator/health" \
                "auth-service:8083:/actuator/health" \
                "user-service:8084:/actuator/health" \
                "ws-gateway:8082:/healthz"; do
  IFS=: read -r svc port path <<< "$svc_port"
  echo "Checking $svc..."
  kubectl --context "$PROFILE" run "test-${svc}-$$" -n "$NAMESPACE" --rm -i --restart=Never \
    --image=curlimages/curl:8.5.0 --command -- \
    curl -sf "http://${svc}:${port}${path}" > /dev/null
  echo "OK: $svc healthy"
done

echo "=== Phase 5: Teardown validation ==="
devspace purge --kube-context "$PROFILE"
kubectl --context "$PROFILE" get deployments -n "$NAMESPACE" --no-headers 2>/dev/null | grep -q . \
  && { echo "FAIL: resources still present after purge"; exit 1; } \
  || echo "OK: clean teardown"

echo "=== All integration tests passed ==="
