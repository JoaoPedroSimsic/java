#!/usr/bin/env bash
# Apply ESO + AWS Secrets Manager stack for staging or prod.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV="${HERMES_ENV:-prod}"

if [[ ! "$ENV" =~ ^(staging|prod)$ ]]; then
  echo "HERMES_ENV must be staging or prod (got: $ENV)"
  exit 1
fi

ESO_OVERLAY="$(cd "$SCRIPT_DIR/../overlays/$ENV" && pwd)"
CONFIG_OVERLAY="$(cd "$SCRIPT_DIR/../config/$ENV" && pwd)"
MANIFESTS_OVERLAY="$(cd "$SCRIPT_DIR/../manifests/$ENV" && pwd)"
ESO_NS="${ESO_NAMESPACE:-external-secrets}"
HERMES_NS="hermes-${ENV}"
STORE_NAME="aws-hermes-${ENV}"
WEBHOOK_TIMEOUT="${ESO_WEBHOOK_TIMEOUT:-300}"

if [[ -z "${ESO_IRSA_ROLE_ARN:-}" ]]; then
  echo "ESO_IRSA_ROLE_ARN is required (terraform output eso_irsa_role_arn)."
  exit 1
fi

if [[ "$ESO_IRSA_ROLE_ARN" == *"PLACEHOLDER"* ]]; then
  echo "ESO_IRSA_ROLE_ARN still contains PLACEHOLDER — set the real IAM role ARN."
  exit 1
fi

wait_for_eso_webhook() {
  echo "Waiting for External Secrets Operator (webhook must accept admissions)..."
  kubectl wait deployment/external-secrets-cert-controller -n "$ESO_NS" --for=condition=Available --timeout="${WEBHOOK_TIMEOUT}s"
  kubectl wait deployment/external-secrets-webhook -n "$ESO_NS" --for=condition=Available --timeout="${WEBHOOK_TIMEOUT}s"
  kubectl wait deployment/external-secrets -n "$ESO_NS" --for=condition=Available --timeout="${WEBHOOK_TIMEOUT}s"

  local elapsed=0
  while true; do
    if kubectl get endpoints external-secrets-webhook -n "$ESO_NS" -o jsonpath='{.subsets[0].addresses[0].ip}' 2>/dev/null | grep -qE '[0-9]'; then
      echo "External Secrets webhook endpoints are ready."
      return 0
    fi
    if (( elapsed >= WEBHOOK_TIMEOUT )); then
      echo "Timed out waiting for external-secrets-webhook endpoints."
      kubectl get pods,svc,endpoints -n "$ESO_NS" 2>/dev/null || true
      return 1
    fi
    sleep 2
    elapsed=$((elapsed + 2))
  done
}

apply_cluster_secret_store() {
  local manifest attempt
  manifest="$(kubectl kustomize "$CONFIG_OVERLAY" --load-restrictor=LoadRestrictionsNone)"
  for attempt in 1 2 3 4 5; do
    if echo "$manifest" | kubectl apply --server-side --force-conflicts -f -; then
      return 0
    fi
    echo "ClusterSecretStore apply failed (attempt $attempt/5); retrying after webhook wait..."
    wait_for_eso_webhook || true
    sleep 3
  done
  echo "Failed to apply ClusterSecretStore after 5 attempts."
  return 1
}

bash "$SCRIPT_DIR/ensure-crds.sh"

echo "Deploying External Secrets Operator controller ($ENV)..."
kubectl kustomize "$ESO_OVERLAY" \
  --enable-helm \
  --load-restrictor=LoadRestrictionsNone \
  | sed "s|PLACEHOLDER_ESO_IRSA_ROLE_ARN|${ESO_IRSA_ROLE_ARN}|g" \
  | kubectl apply --server-side --force-conflicts -f -

wait_for_eso_webhook

echo "Deploying ClusterSecretStore $STORE_NAME..."
apply_cluster_secret_store

echo "Deploying AWS ExternalSecrets into $HERMES_NS..."
kubectl kustomize "$MANIFESTS_OVERLAY" \
  --load-restrictor=LoadRestrictionsNone \
  | kubectl apply --server-side --force-conflicts -f -

echo "ESO + ExternalSecrets applied for $ENV."
