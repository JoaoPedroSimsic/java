#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
source "$SCRIPT_DIR/lib/render-template.sh"

TEMPLATES="$REPO_ROOT/infrastructure/templates"
WITH_POSTGRES=false
WITH_REDIS=false
SERVICE_TYPE="java"

usage() {
  cat <<EOF
Usage: $0 <name> <port> [--with-postgres] [--with-redis] [--type java|go]

Generates:
  - infrastructure/k8s/services/<name>/ (base + dev/staging/prod overlays)
  - Cluster kustomization entries (dev, staging, prod, dev-local-secrets)
  - devspace.yaml image + dev sections
  - .github/services.json entry
  - ExternalSecret manifests (dev/staging/prod) when --with-postgres

With --with-postgres, also runs scripts/new-database.sh.
With --with-redis, also runs scripts/new-database.sh --type redis.

You still need to add application source code and a Dockerfile manually.
EOF
  exit 1
}

[[ $# -ge 2 ]] || usage

SERVICE_NAME="$1"
PORT="$2"
shift 2

if [[ "$SERVICE_NAME" != *-service && "$SERVICE_NAME" != *-gateway ]]; then
  SERVICE_NAME="${SERVICE_NAME}-service"
fi

SHORT_NAME="${SERVICE_NAME%-service}"
SHORT_NAME="${SHORT_NAME%-gateway}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --with-postgres) WITH_POSTGRES=true; shift ;;
    --with-redis) WITH_REDIS=true; shift ;;
    --type)
      SERVICE_TYPE="${2:?}"
      shift 2
      ;;
    -h|--help) usage ;;
    *) echo "Unknown option: $1"; usage ;;
  esac
done

case "$SERVICE_TYPE" in
  java|go) ;;
  *) echo "Unsupported type: $SERVICE_TYPE"; exit 1 ;;
esac

ENV_PREFIX="$(echo "${SHORT_NAME}" | tr '[:lower:]-' '[:upper:]_')_"
DB_SVC="${SHORT_NAME}-db"
DB_DEPLOY="${SHORT_NAME}-postgres"
PG_SECRETS="${SHORT_NAME}-postgres-secrets"
REDIS_DEPLOY="${SHORT_NAME}-redis"
REDIS_SVC="${SHORT_NAME}-cache"
IMAGE="$SERVICE_NAME"
HEALTH_PATH="/actuator/health"

if [[ "$SERVICE_TYPE" == "go" ]]; then
  HEALTH_PATH="/healthz"
fi

if [[ "$SERVICE_NAME" == *-gateway ]]; then
  SVC_PATH="gateways/${SERVICE_NAME}"
  BUILD_CONTEXT="gateways/${SERVICE_NAME}"
  K8S_KIND="gateways"
else
  SVC_PATH="services/${SERVICE_NAME}"
  BUILD_CONTEXT="."
  K8S_KIND="services"
fi

K8S_BASE="$REPO_ROOT/infrastructure/k8s/${K8S_KIND}/${SERVICE_NAME}"
if [[ -d "$K8S_BASE/base" ]]; then
  echo "Service manifests already exist: $K8S_BASE"
  exit 1
fi

if $WITH_POSTGRES; then
  bash "$SCRIPT_DIR/new-database.sh" "$SHORT_NAME" --type postgres
fi
if $WITH_REDIS; then
  bash "$SCRIPT_DIR/new-database.sh" "$SHORT_NAME" --type redis
fi

build_init_containers() {
  local blocks=()
  local tmp
  if $WITH_POSTGRES; then
    tmp="$(mktemp)"
    render_template "$TEMPLATES/init-container-postgres.yaml" "$tmp" \
      DB_SVC="$DB_SVC" SERVICE_NAME="$SERVICE_NAME"
    blocks+=("$(<"$tmp")")
    rm -f "$tmp"
  fi
  if $WITH_REDIS; then
    tmp="$(mktemp)"
    render_template "$TEMPLATES/init-container-redis.yaml" "$tmp" REDIS_SVC="$REDIS_SVC"
    blocks+=("$(<"$tmp")")
    rm -f "$tmp"
  fi
  if [[ ${#blocks[@]} -eq 0 ]]; then
    echo ""
    return
  fi
  {
    echo "      initContainers:"
    local b
    for b in "${blocks[@]}"; do
      while IFS= read -r line; do
        echo "      $line"
      done <<<"$b"
    done
  }
}

echo "Creating K8s service manifests at $K8S_BASE ..."
mkdir -p "$K8S_BASE/base"
INIT_BLOCK="$(build_init_containers)"
deploy_template="deployment-java.yaml"
[[ "$SERVICE_TYPE" == "go" ]] && deploy_template="deployment-go.yaml"
tmp="$(mktemp)"
render_template "$TEMPLATES/service/$deploy_template" "$tmp" \
  SERVICE_NAME="$SERVICE_NAME" PORT="$PORT" IMAGE="$IMAGE" HEALTH_PATH="$HEALTH_PATH"
python3 - "$tmp" "$K8S_BASE/base/deployment.yaml" "$INIT_BLOCK" <<'PY'
import sys
src, dest, init_block = sys.argv[1:4]
text = open(src).read()
if init_block.strip():
    text = text.replace("__INIT_CONTAINERS__\n", init_block.rstrip() + "\n")
else:
    text = text.replace("__INIT_CONTAINERS__\n", "")
open(dest, "w").write(text)
PY
rm -f "$tmp"

for f in "$TEMPLATES/service"/*; do
  [[ -f "$f" ]] || continue
  base="$(basename "$f")"
  [[ "$base" == deployment-java.yaml || "$base" == deployment-go.yaml ]] && continue
  render_template "$f" "$K8S_BASE/base/$base" \
    SERVICE_NAME="$SERVICE_NAME" PORT="$PORT" IMAGE="$IMAGE"
done

secret_store_for_env() {
  case "$1" in
    dev) echo "vault-hermes-dev" ;;
    staging) echo "aws-hermes-staging" ;;
    prod) echo "aws-hermes-prod" ;;
    *) echo "vault-hermes-dev" ;;
  esac
}

for env in dev staging prod; do
  overlay="$K8S_BASE/overlays/$env"
  extra=""
  if $WITH_POSTGRES; then
    extra+="- ../../../../shared/postgres/${SHORT_NAME}-db/overlays/${env}"$'\n'
  fi
  if $WITH_REDIS; then
    extra+="- ../../../../shared/redis/${SHORT_NAME}-redis/overlays/${env}"$'\n'
  fi

  mkdir -p "$overlay"
  {
    cat <<EOF
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources:
- ../../base
EOF
    if $WITH_POSTGRES; then
      echo "- ../../../../shared/postgres/${SHORT_NAME}-db/overlays/${env}"
    fi
    if $WITH_REDIS; then
      echo "- ../../../../shared/redis/${SHORT_NAME}-redis/overlays/${env}"
    fi
    cat <<EOF
configMapGenerator:
- name: ${SERVICE_NAME}-config
  envs:
  - params.env
labels:
- pairs:
    environment: ${env}
  includeSelectors: true
EOF
  } >"$overlay/kustomization.yaml"

  render_template "$TEMPLATES/service-overlay/params.env" "$overlay/params.env" \
    ENV_PREFIX="$ENV_PREFIX" SERVICE_NAME="$SERVICE_NAME" PORT="$PORT" \
    ENVIRONMENT="$env" DB_SVC="$DB_SVC" REDIS_SVC="$REDIS_SVC"

  if $WITH_POSTGRES; then
    store="$(secret_store_for_env "$env")"
    manifest_dir="$REPO_ROOT/infrastructure/k8s/shared/external-secrets/manifests/$env"
    render_template "$TEMPLATES/external-secret-service.yaml" \
      "$manifest_dir/${SERVICE_NAME}-secrets.yaml" \
      SERVICE_NAME="$SERVICE_NAME" \
      SECRET_STORE="$store" \
      VAULT_ENV="$env" \
      VAULT_PATH="hermes/${env}/services/${DB_SVC}/postgres"
    append_unique_line "$manifest_dir/kustomization.yaml" \
      "  - ${SERVICE_NAME}-secrets.yaml"
  fi
done

dev_local="$K8S_BASE/overlays/dev-local"
mkdir -p "$dev_local"
if [[ "$SERVICE_NAME" == *-gateway ]]; then
  cat >"$dev_local/kustomization.yaml" <<EOF
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources:
- ../../base
configMapGenerator:
- name: ${SERVICE_NAME}-config
  envs:
  - params.env
secretGenerator:
- name: ${SERVICE_NAME}-secrets
  envs:
  - secrets.env
labels:
- pairs:
    environment: dev
    secret-source: local
  includeSelectors: true
EOF
else
  {
    cat <<EOF
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources:
- ../../base
EOF
    if $WITH_POSTGRES; then
      echo "- ../../../../shared/postgres/${SHORT_NAME}-db/overlays/dev-local"
    fi
    if $WITH_REDIS; then
      echo "- ../../../../shared/redis/${SHORT_NAME}-redis/overlays/dev"
    fi
    cat <<EOF
configMapGenerator:
- name: ${SERVICE_NAME}-config
  envs:
  - params.env
secretGenerator:
- name: ${SERVICE_NAME}-secrets
  envs:
  - secrets.env
labels:
- pairs:
    environment: dev
    secret-source: local
  includeSelectors: true
EOF
  } >"$dev_local/kustomization.yaml"
fi

for cluster in dev staging prod dev-local-secrets; do
  case "$cluster" in
    dev) env_overlay="dev" ;;
    staging) env_overlay="staging" ;;
    prod) env_overlay="prod" ;;
    dev-local-secrets) env_overlay="dev-local" ;;
  esac
  kust="$REPO_ROOT/infrastructure/k8s/clusters/${cluster}/kustomization.yaml"
  line="  - ../../${K8S_KIND}/${SERVICE_NAME}/overlays/${env_overlay}"
  append_unique_line "$kust" "$line"
done

DEVSPACE="$REPO_ROOT/devspace.yaml"
if grep -q "^  ${SERVICE_NAME}:" "$DEVSPACE" 2>/dev/null; then
  echo "devspace.yaml already has image: $SERVICE_NAME"
else
  python3 - "$DEVSPACE" "$SERVICE_NAME" "$SVC_PATH" "$BUILD_CONTEXT" "$SERVICE_TYPE" "$PORT" "$HEALTH_PATH" <<'PY'
import sys, re
path, name, svc_path, ctx, typ, port, health = sys.argv[1:8]
text = open(path).read()
if re.search(rf'^  {re.escape(name)}:', text, re.M):
    sys.exit(0)

if typ == "go":
    onchange = f"        - {svc_path}/**"
    dockerfile_ctx = ctx
else:
    onchange = "\n".join([
        f"        - {svc_path}/src/**",
        f"        - {svc_path}/pom.xml",
        "        - pom.xml",
        "        - bom/pom.xml",
    ])
    dockerfile_ctx = "."

image_block = f"""  {name}:
    image: {name}
    custom:
      command: |-
        ${{CONTAINER_CMD}} build -t ${{runtime.images.{name}.image}}:${{runtime.images.{name}.tag}} \\
          --network=host -f {svc_path}/Dockerfile {dockerfile_ctx}
      onChange:
{onchange}
"""

dev_block = f"  {name}:\n    imageSelector: {name}\n"
if typ == "java":
    dev_block += f"""    sync:
      - path: {svc_path}/src/main/resources:/app/resources
        excludePaths:
          - "**/*.class"
"""

if "deployments:" in text and image_block.strip() not in text:
    text = text.replace("\ndeployments:", f"\n{image_block}\ndeployments:", 1)
if "hooks:" in text and f"imageSelector: {name}" not in text:
    text = text.replace("\nhooks:", f"\n{dev_block}\nhooks:", 1)

dep_pat = r"(for dep in [^;]+)"
m = re.search(dep_pat, text)
if m and name not in m.group(1):
    text = text.replace(m.group(1), m.group(1) + f" {name}", 1)

check = f'"{name}:{port}:{health}"'
if check not in text:
    text = text.replace(
        '"ws-gateway:8082:/healthz"',
        f'"ws-gateway:8082:/healthz"\n          {check}',
        1,
    )

open(path, "w").write(text)
PY
  echo "Updated devspace.yaml"
fi

append_json_service "$REPO_ROOT/.github/services.json" \
  "$SERVICE_NAME" "$SVC_PATH" "$BUILD_CONTEXT" "$SERVICE_TYPE"

echo ""
echo "Service scaffolding complete for ${SERVICE_NAME} (port ${PORT})."
echo ""
echo "Next steps:"
echo "  1. Create ${SVC_PATH}/ with source code and Dockerfile"
echo "  2. Review ${K8S_BASE}/overlays/*/params.env"
if $WITH_POSTGRES; then
  echo "  3. Ensure .env has POSTGRES_* / APP_* / FLYWAY_* vars, then: make vault-seed"
fi
echo "  4. make validate-manifests && devspace print"
echo "  5. make back  (or back-local with -p local-secrets)"
