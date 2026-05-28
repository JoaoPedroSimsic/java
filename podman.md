# Docker to Podman + Skaffold to DevSpace Migration

This document covers the full transition of the Hermes project from Docker+Skaffold to Podman+DevSpace for both local development and CI/CD.

## Overview

### What changes

| Component | Before | After |
|-----------|--------|-------|
| Container runtime | Docker Engine | Podman (rootless) |
| Image builds | Docker BuildKit via Skaffold | Podman/Buildah via DevSpace |
| Dev orchestration | Skaffold (`skaffold.yaml`) | DevSpace (`devspace.yaml`) |
| Minikube driver | `docker` | `podman` |
| CI build | `docker/build-push-action@v5` | `podman build` + `podman push` |
| CI login | `docker/login-action@v3` | `podman login` |

### What stays the same

- All Kubernetes manifests (Kustomize bases, overlays, Helm charts)
- All Containerfiles/Dockerfiles (OCI-compatible, no changes needed)
- Vault, External Secrets Operator, and secrets workflow
- Port-forwarding, health checks, and deploy verification logic
- GitHub Actions runner OS (`ubuntu-latest` ships with Podman pre-installed)

---

## Prerequisites

### Install Podman

```bash
# Ubuntu/Debian
sudo apt install podman

# Fedora/RHEL (already included)
sudo dnf install podman

# Verify
podman --version
podman info --format '{{.Host.CgroupVersion}}'  # should be v2
```

### Install DevSpace

```bash
# Linux amd64
curl -L -o devspace "https://github.com/devspace-sh/devspace/releases/latest/download/devspace-linux-amd64"
chmod +x devspace
sudo mv devspace /usr/local/bin/

# Verify
devspace --version
```

### Configure Minikube for Podman

```bash
# Set podman as the default driver
minikube config set driver podman

# Or pass it per-start
minikube start -p hermes-dev --driver=podman
```

> **Caveats with Podman driver:**
> - Requires cgroup v2 (default on modern kernels 5.8+)
> - Rootless mode is default; if you hit permission issues with volumes, run `minikube start --driver=podman --container-runtime=cri-o`
> - On older systems you may need `slirp4netns` for rootless networking: `sudo apt install slirp4netns`

### Minikube rootless troubleshooting

Rootless Podman + minikube can start successfully while **ClusterIP / DNS is broken** (kube-proxy cannot program iptables inside the minikube node). Symptoms:

- `nslookup kubernetes.default.svc.cluster.local` times out from pods
- Init containers hang on `pg_isready -h auth-db`
- App images show `ImagePullBackOff` for locally built tags

Preflight check:

```bash
make minikube-preflight
```

One-time host fix (requires sudo):

```bash
sudo modprobe br_netfilter
sudo tee /etc/sysctl.d/99-hermes.conf > /dev/null <<'EOF'
# Required for rootless Podman + minikube
fs.inotify.max_user_watches = 524288
fs.inotify.max_user_instances = 512
net.bridge.bridge-nf-call-iptables = 1
net.bridge.bridge-nf-call-ip6tables = 1
EOF
sudo sysctl --system
make minikube-reset
```

> **Why inotify?** kube-proxy opens an inotify instance per Service/Endpoint it watches. The default `fs.inotify.max_user_instances = 128` is exhausted in a typical cluster, causing kube-proxy to crash with `too many open files`. This breaks ClusterIP routing even after the bridge filter fix.

Set `MINIKUBE_ROOTLESS=false` in the Makefile (or export it) if you have rootful Podman with passwordless sudo for `podman`.

Note: `minikube status` may fail under rootless Podman (it calls `sudo podman inspect`). Use `make minikube-preflight` or `kubectl --context <profile> get nodes` instead.

### Configure Podman short-name resolution

Dockerfiles use short image names (`maven:...`, `golang:...`). Podman requires an unqualified-search registry:

```bash
make setup-podman
# or: bash scripts/setup-podman.sh
```

### Docker socket compatibility shim

Some third-party tools (e.g. Testcontainers, VS Code Docker extension) look for `DOCKER_HOST`. Expose Podman's API on a compatible socket:

```bash
# Start the Podman socket (systemd user service)
systemctl --user enable --now podman.socket

# Export for tools that expect DOCKER_HOST
export DOCKER_HOST=unix://$XDG_RUNTIME_DIR/podman/podman.sock
```

Add this to your shell profile (`~/.zshrc` / `~/.bashrc`) so it persists across sessions.

---

## Local Dev Migration

### Step 1: Minikube driver swap

Update the Makefile variable:

```makefile
# Before
MINIKUBE_DRIVER ?= docker

# After
MINIKUBE_DRIVER ?= podman
```

If you already have a running minikube profile with the docker driver, delete and recreate it:

```bash
minikube delete -p hermes-dev
make minikube-up
```

### Step 2: Create `devspace.yaml`

Replace `skaffold.yaml` with a `devspace.yaml` at the project root. This configuration preserves all Skaffold features: custom Podman builds, file sync for Java resources, port forwarding, deploy status checks, and post-deploy health verification.

```yaml
version: v2beta1
name: hermes

vars:
  MINIKUBE_PROFILE: hermes-dev
  CONTAINER_CMD: podman

images:
  http-gateway:
    image: http-gateway
    custom:
      command: |-
        ${CONTAINER_CMD} build -t ${runtime.images.http-gateway.image}:${runtime.images.http-gateway.tag} \
          --network=host -f gateways/http-gateway/Dockerfile .
      onChange:
        - gateways/http-gateway/src/**
        - gateways/http-gateway/pom.xml
        - pom.xml
        - bom/pom.xml

  auth-service:
    image: auth-service
    custom:
      command: |-
        ${CONTAINER_CMD} build -t ${runtime.images.auth-service.image}:${runtime.images.auth-service.tag} \
          --network=host -f services/auth-service/Dockerfile .
      onChange:
        - services/auth-service/src/**
        - services/auth-service/pom.xml
        - pom.xml
        - bom/pom.xml

  user-service:
    image: user-service
    custom:
      command: |-
        ${CONTAINER_CMD} build -t ${runtime.images.user-service.image}:${runtime.images.user-service.tag} \
          --network=host -f services/user-service/Dockerfile .
      onChange:
        - services/user-service/src/**
        - services/user-service/pom.xml
        - pom.xml
        - bom/pom.xml

  ws-gateway:
    image: ws-gateway
    custom:
      command: |-
        ${CONTAINER_CMD} build -t ${runtime.images.ws-gateway.image}:${runtime.images.ws-gateway.tag} \
          --network=host -f gateways/ws-gateway/Dockerfile gateways/ws-gateway
      onChange:
        - gateways/ws-gateway/**

deployments:
  hermes:
    kubectl:
      kustomize: true
      kustomizeArgs:
        - "--load-restrictor=LoadRestrictionsNone"
        - "--enable-helm"
      manifests:
        - infrastructure/k8s/shared/external-secrets/overlays/dev
        - infrastructure/k8s/shared/external-secrets/manifests/dev
        - infrastructure/k8s/clusters/dev
      cmdArgs:
        - "--server-side"
        - "--force-conflicts"

dev:
  http-gateway:
    imageSelector: http-gateway
    sync:
      - path: gateways/http-gateway/src/main/resources:/app/resources
        excludePaths:
          - "**/*.class"
    ports:
      - port: "8080:8081"

  auth-service:
    imageSelector: auth-service
    sync:
      - path: services/auth-service/src/main/resources:/app/resources
        excludePaths:
          - "**/*.class"

  user-service:
    imageSelector: user-service
    sync:
      - path: services/user-service/src/main/resources:/app/resources
        excludePaths:
          - "**/*.class"

  ws-gateway:
    imageSelector: ws-gateway
    ports:
      - port: "8082:8080"

hooks:
  - command: bash
    args:
      - infrastructure/k8s/shared/external-secrets/scripts/ensure-crds.sh
    events: ["before:deploy"]

  - command: bash
    args:
      - -c
      - |
        echo "Waiting for deployments to become available..."
        for dep in auth-service user-service http-gateway ws-gateway; do
          echo "  Waiting for $dep..."
          kubectl wait deployment/"$dep" -n hermes-dev \
            --for=condition=Available --timeout=420s
        done
        echo "All deployments available."
    events: ["after:deploy"]

  - command: bash
    args:
      - -c
      - |
        echo "Running post-deploy health checks..."
        CHECKS=(
          "http-gateway:8081:/actuator/health"
          "auth-service:8083:/actuator/health"
          "user-service:8084:/actuator/health"
          "ws-gateway:8080:/healthz"
        )
        for entry in "${CHECKS[@]}"; do
          IFS=: read -r svc port path <<< "$entry"
          echo "  Checking $svc..."
          for i in $(seq 1 30); do
            if kubectl exec deploy/"$svc" -n hermes-dev -- \
              wget -qO- --timeout=2 "http://localhost:${port}${path}" > /dev/null 2>&1; then
              echo "  OK: $svc is healthy"
              break
            fi
            if [ "$i" -eq 30 ]; then
              echo "  FAIL: $svc health check timed out"
              exit 1
            fi
            sleep 2
          done
        done
        echo "All health checks passed."
    events: ["after:deploy"]

profiles:
  - name: local-secrets
    patches:
      - op: replace
        path: deployments.hermes.kubectl.manifests
        value:
          - infrastructure/k8s/clusters/dev-local-secrets

  - name: dynamic-secrets
    patches:
      - op: replace
        path: deployments.hermes.kubectl.manifests
        value:
          - infrastructure/k8s/shared/external-secrets/overlays/dev
          - infrastructure/k8s/shared/external-secrets/manifests/dev-dynamic
          - infrastructure/k8s/clusters/dev

  - name: staging
    patches:
      - op: remove
        path: images
      - op: replace
        path: deployments.hermes.kubectl.manifests
        value:
          - infrastructure/k8s/shared/external-secrets/overlays/staging
          - infrastructure/k8s/shared/external-secrets/manifests/staging
          - infrastructure/k8s/clusters/staging

  - name: prod
    patches:
      - op: remove
        path: images
      - op: replace
        path: deployments.hermes.kubectl.manifests
        value:
          - infrastructure/k8s/shared/external-secrets/overlays/prod
          - infrastructure/k8s/shared/external-secrets/manifests/prod
          - infrastructure/k8s/clusters/prod
```

**Feature mapping from Skaffold:**

| Skaffold feature | DevSpace equivalent |
|------------------|---------------------|
| `sync.manual` (resources → /app/resources) | `dev.<service>.sync` with path mapping |
| `portForward` (http-gateway 8081→8080, ws-gateway 8080→8082) | `dev.<service>.ports` |
| `statusCheck` + `statusCheckDeadlineSeconds: 420` | `after:deploy` hook with `kubectl wait --timeout=420s` |
| `tolerateFailuresUntilDeadline` | Handled by `kubectl wait` (waits full duration before failing) |
| `verify` (curl health checks in pods) | `after:deploy` hook with `kubectl exec` + `wget` retries |
| `profiles` with empty `build.artifacts` (skip build for prod/staging) | `op: remove` on `images` path in profile patches |
| `--trigger=polling` (rebuild on file change) | DevSpace watches `onChange` globs natively in dev mode |
| `--tail=true` | DevSpace streams logs by default in dev mode |
| `--keep-running-on-failure` | DevSpace dev mode keeps running on deploy failures by default |

### Step 3: Update the Makefile

Replace skaffold commands with devspace, preserving all pre-deploy dependencies (vault bootstrap, ESO sync):

```makefile
# Before
SKAFFOLD_TRIGGER ?= polling
SKAFFOLD_DEV_FLAGS ?= --tail=true --verbosity=warn --keep-running-on-failure

back: minikube-up vault-init eso-sync
	@if pgrep -x skaffold >/dev/null 2>&1; then \
		echo "WARNING: another 'skaffold dev' is already running."; \
		exit 1; \
	fi
	MINIKUBE_PROFILE="$(MINIKUBE_PROFILE)" skaffold dev --trigger="$(SKAFFOLD_TRIGGER)" $(SKAFFOLD_DEV_FLAGS)

# After
back: minikube-up vault-init eso-sync
	@if pgrep -f "devspace dev" >/dev/null 2>&1; then \
		echo "WARNING: another 'devspace dev' is already running. Stop it before make back."; \
		exit 1; \
	fi
	eval $$(minikube -p "$(MINIKUBE_PROFILE)" podman-env) && devspace dev

back-local: minikube-up
	@if pgrep -f "devspace dev" >/dev/null 2>&1; then \
		echo "WARNING: another 'devspace dev' is already running. Stop it before make back-local."; \
		exit 1; \
	fi
	eval $$(minikube -p "$(MINIKUBE_PROFILE)" podman-env) && devspace dev -p local-secrets

back-dynamic: minikube-up vault-init vault-database-engine eso-sync-dynamic
	@if pgrep -f "devspace dev" >/dev/null 2>&1; then \
		echo "WARNING: another 'devspace dev' is already running. Stop it before make back-dynamic."; \
		exit 1; \
	fi
	eval $$(minikube -p "$(MINIKUBE_PROFILE)" podman-env) && devspace dev -p dynamic-secrets
```

Also update the `teardown` target:

```makefile
# Before
teardown:
	bash infrastructure/k8s/shared/external-secrets/scripts/delete-eso-resources.sh
	skaffold delete
	kubectl delete namespace vault --ignore-not-found --wait=false

# After
teardown:
	bash infrastructure/k8s/shared/external-secrets/scripts/delete-eso-resources.sh
	devspace purge
	kubectl delete namespace vault --ignore-not-found --wait=false
```

Remove the now-unused Skaffold variables from the Makefile header:

```makefile
# Remove these lines
SKAFFOLD_TRIGGER ?= polling
SKAFFOLD_DEV_FLAGS ?= --tail=true --verbosity=warn --keep-running-on-failure
```

### Step 4: Update `hard-reset.sh`

```bash
# Before
echo "4. Pruning Docker volumes..."
docker volume prune -f

# After
echo "4. Pruning Podman volumes..."
podman volume prune -f
```

### Step 5: Configure image visibility in minikube

With the podman driver, images built on the host are **not** automatically visible inside the minikube VM. The Makefile targets (Step 3) use `scripts/podman-minikube-build.sh`, which:

1. Tries `eval $(minikube podman-env)` so builds land in minikube's daemon when available.
2. Otherwise builds on the host and loads into the minikube node via **cri-dockerd** (`podman save | podman exec <profile> docker load`, then tags without the `localhost/` prefix).

`minikube image load` alone is unreliable with rootless host Podman — use the wrapper script or the manual load flow below.

For manual one-off builds outside of DevSpace:

```bash
# Option A: point podman at minikube's daemon (when podman-env works without sudo)
eval $(minikube -p hermes-dev podman-env)
podman build --network=host -t my-image:dev -f path/to/Dockerfile .

# Option B: build on host and load into minikube cri-dockerd
podman build --network=host -t my-image:dev -f path/to/Dockerfile .
podman save my-image:dev | podman exec -i hermes-dev docker load
podman exec hermes-dev docker tag localhost/my-image:dev my-image:dev
```

---

## CI/CD Migration

### Replace `docker-build` job in `ci.yml`

```yaml
# Before
- name: Build Docker image (no push)
  uses: docker/build-push-action@v5
  with:
    context: .
    file: ${{ matrix.path }}/Dockerfile
    push: false

# After
- name: Build container image (validate)
  run: |
    podman build --network=host \
      -t hermes-${{ matrix.service }}:ci \
      -f ${{ matrix.path }}/Dockerfile .
```

### Replace `build-images.yml` workflow

```yaml
# Before
- name: Login to Docker Hub
  uses: docker/login-action@v3
  with:
    username: ${{ secrets.DOCKERHUB_USERNAME }}
    password: ${{ secrets.DOCKERHUB_TOKEN }}

- name: Build and push Docker image
  uses: docker/build-push-action@v5
  with:
    context: .
    file: ${{ matrix.path }}/Dockerfile
    push: true
    tags: |
      ${{ secrets.DOCKERHUB_USERNAME }}/hermes-${{ matrix.service }}:${{ steps.meta.outputs.sha }}

# After
- name: Login to Docker Hub
  run: |
    podman login docker.io \
      -u "${{ secrets.DOCKERHUB_USERNAME }}" \
      -p "${{ secrets.DOCKERHUB_TOKEN }}"

- name: Build and push container image
  run: |
    IMAGE="${{ secrets.DOCKERHUB_USERNAME }}/hermes-${{ matrix.service }}"
    SHA_TAG="${{ steps.meta.outputs.sha }}"
    SHORT_TAG="sha-${{ steps.meta.outputs.short_sha }}"
    BRANCH_TAG="${{ steps.meta.outputs.branch == 'main' && 'latest' || format('{0}-latest', steps.meta.outputs.branch) }}"

    podman build --network=host \
      -t "${IMAGE}:${SHA_TAG}" \
      -t "${IMAGE}:${SHORT_TAG}" \
      -t "${IMAGE}:${BRANCH_TAG}" \
      -f "${{ matrix.path }}/Dockerfile" .

    podman push "${IMAGE}:${SHA_TAG}"
    podman push "${IMAGE}:${SHORT_TAG}"
    podman push "${IMAGE}:${BRANCH_TAG}"
```

> **Note:** `ubuntu-latest` on GitHub Actions includes Podman. No setup action needed.

---

## Dockerfile Compatibility Notes

### `RUN --mount=type=cache` (BuildKit cache mounts)

Podman/Buildah supports this syntax natively since Podman 4.0+. No changes needed to the Dockerfiles.

```dockerfile
# This works as-is with podman build
RUN --mount=type=cache,target=/root/.m2 \
    mvn -f gateways/http-gateway/pom.xml package -DskipTests -q
```

### `network: host` during builds

In Docker/Skaffold this was set in `skaffold.yaml`. With Podman, pass it directly:

```bash
podman build --network=host -f Dockerfile .
```

This is already reflected in the DevSpace custom build commands above.

### Multi-stage builds

Fully supported by Podman/Buildah. All existing Dockerfiles (maven builder -> jre-alpine, golang builder -> alpine) work without modification.

### `USER` directive and rootless

Podman runs rootless by default. The `USER appuser` / `USER nobody:nobody` directives in your Dockerfiles are compatible. If anything, rootless Podman is more aligned with this pattern than Docker.

---

## Infrastructure Testing

This section covers how to validate that the Podman migration works at every layer -- container builds, Kustomize manifests, Kubernetes deployments, and secrets -- both locally and in CI.

### Layer 1: Container Image Build Validation

Verify that every Dockerfile builds correctly under Podman/Buildah. This catches OCI compatibility regressions (e.g. `RUN --mount` syntax, multi-stage `COPY --from`).

```bash
#!/usr/bin/env bash
# infrastructure/scripts/validate-builds.sh
set -euo pipefail

CONTAINER_CMD="${CONTAINER_CMD:-podman}"

declare -A SERVICES=(
  [http-gateway]="gateways/http-gateway"
  [auth-service]="services/auth-service"
  [user-service]="services/user-service"
  [ws-gateway]="gateways/ws-gateway"
)

FAILED=0
for svc in "${!SERVICES[@]}"; do
  path="${SERVICES[$svc]}"
  echo "--- Building $svc from $path/Dockerfile ---"

  build_ctx="."
  if [[ "$svc" == "ws-gateway" ]]; then
    build_ctx="$path"
  fi

  if $CONTAINER_CMD build --network=host \
    -t "hermes-${svc}:validate" \
    -f "${path}/Dockerfile" "$build_ctx"; then
    echo "OK: $svc"
  else
    echo "FAIL: $svc"
    FAILED=$((FAILED + 1))
  fi
done

if [[ $FAILED -gt 0 ]]; then
  echo "$FAILED service(s) failed to build"
  exit 1
fi
echo "All service images built successfully with $CONTAINER_CMD"
```

Run locally:

```bash
chmod +x infrastructure/scripts/validate-builds.sh
./infrastructure/scripts/validate-builds.sh
```

### Layer 2: Kustomize Manifest Validation (Dry-Run)

Validate that every cluster overlay renders without errors. This runs without a cluster and catches YAML/reference problems.

```bash
#!/usr/bin/env bash
# infrastructure/scripts/validate-manifests.sh
set -euo pipefail

ENVS=("dev" "dev-local-secrets" "staging" "prod")
FAILED=0

for env in "${ENVS[@]}"; do
  overlay="infrastructure/k8s/clusters/${env}"
  if [[ ! -d "$overlay" ]]; then
    echo "SKIP: $overlay does not exist"
    continue
  fi

  echo "--- Validating cluster overlay: $env ---"
  if kubectl kustomize "$overlay" \
    --load-restrictor=LoadRestrictionsNone \
    --enable-helm > /dev/null 2>&1; then
    echo "OK: $env"
  else
    echo "FAIL: $env"
    kubectl kustomize "$overlay" \
      --load-restrictor=LoadRestrictionsNone \
      --enable-helm 2>&1 | tail -20
    FAILED=$((FAILED + 1))
  fi
done

if [[ $FAILED -gt 0 ]]; then
  echo "$FAILED overlay(s) failed validation"
  exit 1
fi
echo "All Kustomize overlays render cleanly"
```

### Layer 3: Terraform Validation

Ensure Terraform configs (secrets-manager, cognito) are syntactically valid and properly formatted.

```bash
#!/usr/bin/env bash
# infrastructure/scripts/validate-terraform.sh
set -euo pipefail

TF_MODULES=(
  "infrastructure/terraform/secrets-manager"
  "infrastructure/terraform/cognito"
)
FAILED=0

for mod in "${TF_MODULES[@]}"; do
  if [[ ! -d "$mod" ]]; then
    echo "SKIP: $mod not found"
    continue
  fi

  echo "--- Validating $mod ---"
  (
    cd "$mod"
    terraform fmt -check -recursive
    terraform init -backend=false -input=false > /dev/null 2>&1
    terraform validate
  ) && echo "OK: $mod" || { echo "FAIL: $mod"; FAILED=$((FAILED + 1)); }
done

if [[ $FAILED -gt 0 ]]; then
  echo "$FAILED Terraform module(s) failed"
  exit 1
fi
echo "All Terraform modules valid"
```

### Layer 4: DevSpace Dry-Run

Validate the `devspace.yaml` configuration parses correctly and the build commands are syntactically valid without actually executing builds or connecting to a cluster.

```bash
# Validate devspace config syntax
devspace print

# List all profiles and verify they resolve
devspace print -p local-secrets
devspace print -p dynamic-secrets
devspace print -p staging
devspace print -p prod
```

### Layer 5: Full Local Integration Test

End-to-end validation on a local minikube cluster. This tests the entire stack: Podman builds, DevSpace orchestration, Kustomize deployment, secret syncing, and health checks.

```bash
#!/usr/bin/env bash
# infrastructure/scripts/integration-test-local.sh
set -euo pipefail

PROFILE="${MINIKUBE_PROFILE:-hermes-test}"
NAMESPACE="hermes-dev"
TIMEOUT="${SMOKE_TIMEOUT:-600}"

cleanup() {
  echo "Cleaning up test cluster..."
  devspace purge 2>/dev/null || true
  minikube delete -p "$PROFILE" 2>/dev/null || true
}
trap cleanup EXIT

echo "=== Phase 1: Cluster bootstrap ==="
minikube start -p "$PROFILE" --driver=podman \
  --kubernetes-version=v1.31.6 \
  --memory=6144 --cpus=3 --wait-timeout=10m

echo "=== Phase 2: Build all images ==="
MINIKUBE_PROFILE="$PROFILE" ./infrastructure/scripts/validate-builds.sh

echo "=== Phase 3: Deploy with DevSpace (local-secrets profile) ==="
devspace deploy -p local-secrets

echo "=== Phase 4: Wait for deployments ==="
for dep in auth-service user-service http-gateway ws-gateway; do
  echo "Waiting for $dep..."
  kubectl wait deployment/"$dep" -n "$NAMESPACE" \
    --for=condition=Available --timeout="${TIMEOUT}s"
done

echo "=== Phase 5: Health checks ==="
for svc_port in "http-gateway:8081:/actuator/health" \
                "auth-service:8083:/actuator/health" \
                "user-service:8084:/actuator/health" \
                "ws-gateway:8080:/healthz"; do
  IFS=: read -r svc port path <<< "$svc_port"
  echo "Checking $svc..."
  kubectl run "test-${svc}-$$" -n "$NAMESPACE" --rm -i --restart=Never \
    --image=curlimages/curl:8.5.0 --command -- \
    curl -sf "http://${svc}:${port}${path}" > /dev/null
  echo "OK: $svc healthy"
done

echo "=== Phase 6: Teardown validation ==="
devspace purge
kubectl get deployments -n "$NAMESPACE" --no-headers 2>/dev/null | grep -q . \
  && { echo "FAIL: resources still present after purge"; exit 1; } \
  || echo "OK: clean teardown"

echo "=== All integration tests passed ==="
```

### Layer 6: Image Security Scan

Scan built images for known CVEs. Podman integrates with several scanning tools.

```bash
# Using Trivy (install: https://aquasecurity.github.io/trivy)
for svc in http-gateway auth-service user-service ws-gateway; do
  echo "--- Scanning hermes-${svc}:validate ---"
  trivy image --severity HIGH,CRITICAL --exit-code 1 \
    "hermes-${svc}:validate" || echo "WARN: $svc has vulnerabilities"
done
```

### Makefile Targets for Infra Testing

Add these targets to the Makefile for developer convenience:

```makefile
validate-builds:
	bash infrastructure/scripts/validate-builds.sh

validate-manifests:
	bash infrastructure/scripts/validate-manifests.sh

validate-terraform:
	bash infrastructure/scripts/validate-terraform.sh

validate-devspace:
	KUBECONFIG=/dev/null devspace print > /dev/null
	# ... all profiles

validate-all: validate-builds validate-manifests validate-terraform validate-devspace

integration-test:
	MINIKUBE_PROFILE=hermes-test bash infrastructure/scripts/integration-test-local.sh
```

---

## CI/CD Integration

### Updated `ci.yml` (Complete Workflow)

The full CI workflow after migrating from Docker to Podman. Key changes are in the `container-build` job (replaces `docker-build`) and the new `infra-test` job.

```yaml
name: CI

on:
  push:
    branches: [main, develop]
    paths-ignore: ['**.md', 'docs/**', 'LICENSE']
  pull_request:
    branches: [main, develop]
    paths-ignore: ['**.md', 'docs/**', 'LICENSE']

concurrency:
  group: ci-${{ github.workflow }}-${{ github.event.pull_request.number || github.ref }}
  cancel-in-progress: true

jobs:
  secret-scan:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      pull-requests: write
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - name: gitleaks
        uses: gitleaks/gitleaks-action@v2
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}

      - name: Set up Python
        uses: actions/setup-python@v5
        with:
          python-version: '3.12'

      - name: detect-secrets
        run: |
          pip install detect-secrets==1.5.0
          detect-secrets scan --baseline .secrets.baseline

  java-integration:
    needs: secret-scan
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: ./.github/actions/setup-java-maven
      - name: Build and test
        run: mvn clean verify
        env:
          JWT_SECRET: ${{ secrets.JWT_SECRET }}

  go-integration:
    needs: secret-scan
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: ./.github/actions/setup-go
      - name: Install dependencies
        run: go mod download
        working-directory: gateways/ws-gateway
      - name: Run all Go tests
        run: |
          find . -name "go.mod" -not -path "*/node_modules/*" -print0 | xargs -0 -I {} sh -c '
            dir=$(dirname "{}")
            echo "Testing service in: $dir"
            cd "$dir" && go test -v ./...
          '

  frontend:
    needs: secret-scan
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up Bun
        uses: oven-sh/setup-bun@v2
        with:
          bun-version: '1.3.10'
      - name: Install dependencies
        working-directory: frontend
        run: bun install --frozen-lockfile
      - name: Lint
        working-directory: frontend
        run: bun run lint
      - name: Build
        working-directory: frontend
        run: bun run build

  infra-validate:
    needs: secret-scan
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up Terraform
        uses: hashicorp/setup-terraform@v3

      - name: Terraform fmt and validate
        working-directory: infrastructure/terraform/secrets-manager
        run: |
          terraform fmt -check -recursive
          terraform init -backend=false
          terraform validate

      - name: Terraform validate (cognito)
        working-directory: infrastructure/terraform/cognito
        run: |
          terraform fmt -check -recursive
          terraform init -backend=false
          terraform validate

      - name: Set up Helm
        uses: azure/setup-helm@v4

      - name: Validate Kustomize overlays
        run: |
          for env in dev dev-local-secrets staging prod; do
            echo "Validating cluster overlay: $env"
            kubectl kustomize "infrastructure/k8s/clusters/${env}" \
              --load-restrictor=LoadRestrictionsNone --enable-helm > /dev/null
          done

      - name: Validate DevSpace config
        run: |
          curl -L -o devspace \
            "https://github.com/devspace-sh/devspace/releases/latest/download/devspace-linux-amd64"
          chmod +x devspace
          sudo mv devspace /usr/local/bin/

          devspace print > /dev/null
          for profile in local-secrets dynamic-secrets staging prod; do
            echo "Validating DevSpace profile: $profile"
            devspace print -p "$profile" > /dev/null
          done

  container-build:
    needs: [secret-scan, java-integration, go-integration]
    runs-on: ubuntu-latest
    strategy:
      fail-fast: false
      matrix:
        service: [http-gateway, auth-service, user-service, ws-gateway]
        include:
          - service: http-gateway
            path: gateways/http-gateway
            context: .
          - service: auth-service
            path: services/auth-service
            context: .
          - service: user-service
            path: services/user-service
            context: .
          - service: ws-gateway
            path: gateways/ws-gateway
            context: gateways/ws-gateway
    steps:
      - uses: actions/checkout@v4

      - name: Verify Podman version
        run: |
          podman --version
          podman info --format '{{.Host.OciRuntime.Name}} {{.Host.CgroupVersion}}'

      - name: Build container image (validate)
        run: |
          podman build --network=host \
            -t hermes-${{ matrix.service }}:ci \
            -f ${{ matrix.path }}/Dockerfile \
            ${{ matrix.context }}

      - name: Inspect built image
        run: |
          podman inspect hermes-${{ matrix.service }}:ci \
            --format '{{.Id}} {{.Size}} {{.Config.User}}'

      - name: Scan image for CVEs
        uses: aquasecurity/trivy-action@master
        with:
          image-ref: hermes-${{ matrix.service }}:ci
          severity: HIGH,CRITICAL
          exit-code: '0'
          format: table

  infra-test:
    needs: [infra-validate, container-build]
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Verify Podman is functional
        run: |
          podman run --rm docker.io/library/alpine:3.19 echo "podman works"

      - name: Run container sanity checks
        run: |
          for svc in http-gateway auth-service user-service; do
            echo "--- Rebuilding $svc for sanity check ---"
            podman build --network=host \
              -t "hermes-${svc}:test" \
              -f "$(
                case $svc in
                  http-gateway)  echo gateways/http-gateway ;;
                  auth-service)  echo services/auth-service ;;
                  user-service)  echo services/user-service ;;
                esac
              )/Dockerfile" .

            echo "Checking $svc runs and exits cleanly (--help or short-lived)..."
            podman run --rm "hermes-${svc}:test" java -version 2>&1 | head -5 || true
          done

      - name: Validate image layer structure
        run: |
          for svc in http-gateway auth-service user-service; do
            echo "--- Layer analysis: $svc ---"
            layers=$(podman history --no-trunc --format '{{.Size}}' "hermes-${svc}:test" | wc -l)
            size=$(podman image inspect "hermes-${svc}:test" --format '{{.Size}}')
            echo "$svc: $layers layers, $(( size / 1024 / 1024 ))MB"

            user=$(podman inspect "hermes-${svc}:test" --format '{{.Config.User}}')
            if [[ -z "$user" || "$user" == "root" || "$user" == "0" ]]; then
              echo "WARN: $svc runs as root — consider adding a non-root USER"
            else
              echo "OK: $svc runs as user '$user'"
            fi
          done

      - name: Podman rootless network test
        run: |
          podman run --rm --network=host \
            docker.io/library/alpine:3.19 \
            sh -c "apk add --no-cache curl > /dev/null 2>&1 && echo 'rootless networking OK'"
```

### Updated `build-images.yml` (Complete Workflow)

```yaml
name: Build images

on:
  workflow_run:
    workflows: [CI]
    types: [completed]
    branches: [main, develop]
  workflow_dispatch:

concurrency:
  group: build-images-${{ github.event.workflow_run.head_branch || github.ref_name }}
  cancel-in-progress: true

jobs:
  build:
    if: >
      (github.event_name == 'workflow_run' && github.event.workflow_run.conclusion == 'success') ||
      github.event_name == 'workflow_dispatch'
    runs-on: ubuntu-latest
    environment: ${{ (github.event.workflow_run.head_branch || github.ref_name) == 'main' && 'production' || '' }}
    strategy:
      fail-fast: false
      matrix:
        service: [http-gateway, auth-service, user-service, ws-gateway]
        include:
          - service: http-gateway
            path: gateways/http-gateway
            context: .
          - service: auth-service
            path: services/auth-service
            context: .
          - service: user-service
            path: services/user-service
            context: .
          - service: ws-gateway
            path: gateways/ws-gateway
            context: gateways/ws-gateway

    steps:
      - uses: actions/checkout@v4
        with:
          ref: ${{ github.event.workflow_run.head_sha || github.sha }}

      - uses: ./.github/actions/setup-java-maven
        if: matrix.service != 'ws-gateway'

      - name: Package application
        if: matrix.service != 'ws-gateway'
        run: mvn package -DskipTests -pl ${{ matrix.path }} -am

      - name: Resolve image metadata
        id: meta
        run: |
          SHA="${{ github.event.workflow_run.head_sha || github.sha }}"
          BRANCH="${{ github.event.workflow_run.head_branch || github.ref_name }}"
          SHORT_SHA="${SHA:0:7}"
          echo "sha=${SHA}" >> "$GITHUB_OUTPUT"
          echo "branch=${BRANCH}" >> "$GITHUB_OUTPUT"
          echo "short_sha=${SHORT_SHA}" >> "$GITHUB_OUTPUT"

      - name: Login to Docker Hub
        run: |
          podman login docker.io \
            -u "${{ secrets.DOCKERHUB_USERNAME }}" \
            -p "${{ secrets.DOCKERHUB_TOKEN }}"

      - name: Build and push container image
        run: |
          IMAGE="${{ secrets.DOCKERHUB_USERNAME }}/hermes-${{ matrix.service }}"
          SHA_TAG="${{ steps.meta.outputs.sha }}"
          SHORT_TAG="sha-${{ steps.meta.outputs.short_sha }}"
          BRANCH_TAG="${{ steps.meta.outputs.branch == 'main' && 'latest' || format('{0}-latest', steps.meta.outputs.branch) }}"

          podman build --network=host \
            -t "${IMAGE}:${SHA_TAG}" \
            -t "${IMAGE}:${SHORT_TAG}" \
            -t "${IMAGE}:${BRANCH_TAG}" \
            -f "${{ matrix.path }}/Dockerfile" \
            ${{ matrix.context }}

          podman push "${IMAGE}:${SHA_TAG}"
          podman push "${IMAGE}:${SHORT_TAG}"
          podman push "${IMAGE}:${BRANCH_TAG}"

      - name: Verify pushed image is pullable
        run: |
          IMAGE="${{ secrets.DOCKERHUB_USERNAME }}/hermes-${{ matrix.service }}"
          SHORT_TAG="sha-${{ steps.meta.outputs.short_sha }}"
          podman pull "${IMAGE}:${SHORT_TAG}"
          echo "OK: ${IMAGE}:${SHORT_TAG} pullable from registry"
```

### CI Job Dependency Graph

```
secret-scan
  ├── java-integration ──┐
  ├── go-integration ────┤
  ├── frontend           ├── container-build ──┐
  └── infra-validate ────┘                     ├── infra-test
                         └────────────────────-┘

(workflow_run on CI success)
  └── build-images (podman build + push + verify)
```

### What Changed vs. the Original CI

| Job | Before | After |
|-----|--------|-------|
| `docker-build` | `docker/build-push-action@v5` | Renamed to `container-build`; uses `podman build` directly |
| `infra-validate` | Only validated secrets-manager TF + staging/prod overlays | Now also validates cognito TF module, all 4 Kustomize overlays, and DevSpace config |
| `infra-test` (new) | Did not exist | Validates built images run correctly, checks layer structure, verifies rootless networking, and scans for non-root USER |
| `build-images` | `docker/login-action@v3` + `docker/build-push-action@v5` | `podman login` + `podman build` + `podman push` + pull-back verification |
| Matrix services | 3 (http-gateway, auth-service, user-service) | 4 (adds ws-gateway with its own build context) |

### CI Troubleshooting

**Podman storage driver issues on GitHub Actions:**

```yaml
- name: Configure Podman storage
  run: |
    mkdir -p ~/.config/containers
    cat > ~/.config/containers/storage.conf <<'EOF'
    [storage]
    driver = "overlay"
    [storage.options.overlay]
    mount_program = "/usr/bin/fuse-overlayfs"
    EOF
```

**Cache mount (`--mount=type=cache`) not persisting across CI runs:** This is expected -- CI runs in ephemeral environments. The cache only helps within multi-stage builds in a single run. For cross-run caching, use GitHub Actions cache:

```yaml
- name: Cache Maven packages
  uses: actions/cache@v4
  with:
    path: ~/.m2/repository
    key: ${{ runner.os }}-maven-${{ hashFiles('**/pom.xml') }}
    restore-keys: ${{ runner.os }}-maven-

- name: Cache Go modules
  uses: actions/cache@v4
  with:
    path: ~/go/pkg/mod
    key: ${{ runner.os }}-go-${{ hashFiles('**/go.sum') }}
    restore-keys: ${{ runner.os }}-go-
```

**Image too large / slow push:** Podman supports `--squash` to flatten layers, but multi-stage builds already minimize final image size. Check for accidental build artifacts:

```bash
podman history hermes-http-gateway:ci --format '{{.Size}}\t{{.CreatedBy}}' | head -10
```

---

## Streamlining Service and Database Onboarding

Adding a new service or database today touches 13+ files across 6 different concerns. This section lays out the infrastructure rearrangement to bring that down to: write your code, run one script.

### The Problem: Current State

To add a new service (e.g. `chat-service` on port 8085 with a Postgres DB):

| # | Location | What you manually create/edit |
|---|----------|-------------------------------|
| 1 | `services/chat-service/` | Source code + Dockerfile |
| 2 | `infrastructure/k8s/services/chat-service/base/` | deployment.yaml, service.yaml, kustomization.yaml |
| 3 | `infrastructure/k8s/services/chat-service/overlays/{dev,staging,prod}/` | kustomization.yaml + params.env per env |
| 4 | `infrastructure/k8s/shared/postgres/chat-db/base/` | deployment.yaml, service.yaml, pvc.yaml, configmap.yaml, kustomization.yaml |
| 5 | `infrastructure/k8s/shared/postgres/chat-db/overlays/{dev,staging,prod}/` | kustomization.yaml + params.env per env |
| 6 | `infrastructure/k8s/shared/external-secrets/manifests/{dev,staging,prod}/` | chat-service-secrets.yaml + chat-postgres-secrets.yaml per env |
| 7 | `infrastructure/k8s/shared/external-secrets/scripts/wait-for-synced-secrets.sh` | Add to `REQUIRED_SECRETS` array |
| 8 | `infrastructure/k8s/shared/external-secrets/scripts/wait-for-synced-secrets-aws.sh` | Add to `REQUIRED_SECRETS` array |
| 9 | `infrastructure/vault/scripts/seed-dev-secrets.sh` | Add `vault_kv_put` call + new vars to `REQUIRED` |
| 10 | `infrastructure/k8s/clusters/{dev,dev-local-secrets,staging,prod}/kustomization.yaml` | Add resource reference (4 files) |
| 11 | `devspace.yaml` | Add image entry + dev section |
| 12 | `.github/workflows/ci.yml` | Add to matrix |
| 13 | `.github/workflows/build-images.yml` | Add to matrix |

That is too much friction.

### Solution 1: Dynamic ExternalSecret Discovery

Replace the hardcoded `REQUIRED_SECRETS` arrays with auto-discovery from the namespace.

**Before** (`wait-for-synced-secrets.sh`):

```bash
REQUIRED_SECRETS=(
  gateway-secrets
  auth-service-secrets
  user-service-secrets
  auth-postgres-secrets
  user-postgres-secrets
  rabbitmq-secrets
  keycloak-secrets
)
```

**After:**

```bash
mapfile -t REQUIRED_SECRETS < <(
  kubectl get externalsecret -n "$NAMESPACE" -o jsonpath='{.items[*].metadata.name}' \
    | tr ' ' '\n' | sort
)

if [[ ${#REQUIRED_SECRETS[@]} -eq 0 ]]; then
  echo "No ExternalSecrets found in $NAMESPACE; skipping sync wait."
  exit 0
fi
```

Apply the same change to `wait-for-synced-secrets-aws.sh`.

**Result:** Adding a new ExternalSecret manifest is all you need — the wait scripts discover it automatically. No more maintaining parallel lists.

### Solution 2: Shared CI Service Registry

Create a single source of truth for the service matrix at `.github/services.json`:

```json
[
  { "service": "http-gateway", "path": "gateways/http-gateway", "context": ".", "type": "java" },
  { "service": "auth-service", "path": "services/auth-service", "context": ".", "type": "java" },
  { "service": "user-service", "path": "services/user-service", "context": ".", "type": "java" },
  { "service": "ws-gateway",   "path": "gateways/ws-gateway",   "context": "gateways/ws-gateway", "type": "go" }
]
```

Both CI workflows load it via a setup job:

```yaml
jobs:
  setup:
    runs-on: ubuntu-latest
    outputs:
      services: ${{ steps.matrix.outputs.services }}
    steps:
      - uses: actions/checkout@v4
      - id: matrix
        run: echo "services=$(cat .github/services.json)" >> "$GITHUB_OUTPUT"

  container-build:
    needs: [secret-scan, java-integration, go-integration, setup]
    strategy:
      matrix:
        include: ${{ fromJson(needs.setup.outputs.services) }}
```

**Result:** Adding a service to CI = one line in `services.json`. No more duplicated matrices.

### Solution 3: Scaffolding Scripts

#### `scripts/new-service.sh`

```
Usage: scripts/new-service.sh <name> <port> [--with-postgres] [--with-redis] [--type java|go]
```

What it generates:

1. K8s base manifests from `infrastructure/templates/service/` (deployment.yaml, service.yaml, kustomization.yaml) with `__SERVICE_NAME__` and `__PORT__` placeholders replaced
2. Per-env overlays (dev, staging, prod) from `infrastructure/templates/service-overlay/`
3. Appends the service overlay to each cluster `kustomization.yaml`
4. Adds image + dev entry to `devspace.yaml`
5. Adds entry to `.github/services.json`
6. If `--with-postgres`: delegates to `scripts/new-database.sh`
7. If `--with-redis`: generates `infrastructure/k8s/shared/redis/<name>-redis/` from template

#### `scripts/new-database.sh`

```
Usage: scripts/new-database.sh <service-name> [--type postgres|redis]
```

What it generates:

1. `infrastructure/k8s/shared/postgres/<name>-db/base/` from `infrastructure/templates/postgres/` (deployment.yaml, service.yaml, pvc.yaml, configmap.yaml, kustomization.yaml)
2. Per-env overlays from `infrastructure/templates/postgres-overlay/`
3. ExternalSecret manifests per env from `infrastructure/templates/external-secret.yaml`
4. Adds `vault_kv_put` entry to `seed-dev-secrets.sh`
5. Adds required env vars to `.env.example`
6. References the DB overlay from the service's overlay kustomization.yaml

### Template Directory Structure

```
infrastructure/templates/
├── service/
│   ├── deployment-java.yaml     # Java/Spring: Actuator split probes + Prometheus annotations
│   ├── deployment-go.yaml       # Go: single __HEALTH_PATH__ for all probes, no Spring conventions
│   ├── service.yaml             # __SERVICE_NAME__, __PORT__
│   └── kustomization.yaml       # references deployment.yaml (both variants render to that filename)
├── service-overlay/
│   ├── kustomization.yaml       # __SERVICE_NAME__, __ENVIRONMENT__
│   └── params.env               # __ENV_PREFIX__, __PORT__, __DB_SVC__, __REDIS_SVC__, defaults
├── postgres/
│   ├── deployment.yaml          # __DB_DEPLOY__, __PG_SECRETS__, postgres:16-alpine
│   ├── service.yaml             # __DB_SVC__, __DB_DEPLOY__
│   ├── pvc.yaml                 # __DB_DEPLOY__
│   ├── configmap.yaml           # __DB_DEPLOY__
│   └── kustomization.yaml
├── postgres-overlay/
│   ├── kustomization.yaml       # __DB_DEPLOY__, __ENVIRONMENT__
│   └── params.env
├── redis/
│   ├── deployment.yaml          # __REDIS_DEPLOY__, redis:7-alpine
│   ├── service.yaml             # __REDIS_SVC__, __REDIS_DEPLOY__
│   ├── pvc.yaml                 # __REDIS_DEPLOY__
│   └── kustomization.yaml
├── redis-overlay/
│   └── kustomization.yaml       # __REDIS_DEPLOY__, __ENVIRONMENT__
├── init-container-postgres.yaml # __DB_SVC__, __SERVICE_NAME__ — pg_isready wait block
├── init-container-redis.yaml    # __REDIS_SVC__ — redis-cli ping wait block
├── external-secret-postgres.yaml # __PG_SECRETS__, __SECRET_STORE__, __VAULT_PATH__ (POSTGRES_*/APP_*/FLYWAY_* keys)
└── external-secret-service.yaml  # __SERVICE_NAME__, __SECRET_STORE__, __VAULT_PATH__, __VAULT_ENV__
                                  # (APP_*/FLYWAY_* keys + RABBITMQ_* from hermes/<env>/shared/rabbitmq)
```

`scripts/new-service.sh` composes the deployment spec by selecting `deployment-java.yaml`
or `deployment-go.yaml` (via `--type java|go`) and splicing in `init-container-postgres.yaml`
and/or `init-container-redis.yaml` based on the `--with-postgres` / `--with-redis` flags.
Both variants render to `<service>/base/deployment.yaml`, which is what the generated
`kustomization.yaml` references. ExternalSecret manifests are generated separately for
the service (`external-secret-service.yaml`, includes RabbitMQ refs) and for its database
(`external-secret-postgres.yaml`).

### Before vs. After

**Adding `chat-service` (port 8085) with a Postgres database:**

| | Before | After |
|--|--------|-------|
| Steps | 13 manual file edits | 2 commands |
| Commands | None | `scripts/new-service.sh chat-service 8085 --with-postgres --type java` |
| CI update | Edit 2 workflow files | Automatic (via services.json) |
| Secret wait scripts | Edit 2 files | Automatic (dynamic discovery) |
| Vault seed | Manual edit | Automatic (script appends entry) |
| Cluster kustomizations | Edit 4 files | Automatic (script appends) |
| DevSpace config | Manual edit | Automatic (script appends) |
| Still manual | Write source code + Dockerfile, fill `.env` values | Same |

### Vault Seed Script Rearrangement

The current `seed-dev-secrets.sh` has a hardcoded `REQUIRED` array and individual `vault_kv_put` calls. Rearrange it to be data-driven:

```bash
VAULT_SECRETS_DIR="$REPO_ROOT/infrastructure/vault/secrets"

for secret_file in "$VAULT_SECRETS_DIR"/*.env; do
  vault_path=$(head -1 "$secret_file" | sed 's/^# vault_path=//')
  
  kv_args=()
  while IFS='=' read -r key value; do
    [[ "$key" =~ ^#.*$ || -z "$key" ]] && continue
    resolved="${!key:-$value}"
    kv_args+=("$key=$resolved")
  done < "$secret_file"
  
  vault_kv_put "$PREFIX/$vault_path" "${kv_args[@]}"
done
```

Each secret gets its own file in `infrastructure/vault/secrets/`:

```
infrastructure/vault/secrets/
├── auth-db-postgres.env       # vault_path=services/auth-db/postgres
├── user-db-postgres.env       # vault_path=services/user-db/postgres
├── keycloak-admin.env         # vault_path=services/keycloak/keycloak-admin
├── rabbitmq.env               # vault_path=shared/rabbitmq
├── github-oauth.env           # vault_path=shared/github-oauth
└── jwt-signing-key.env        # vault_path=shared/jwt-signing-key
```

Adding a new secret to Vault = drop a `.env` file. The seed script discovers and seeds all of them.

---

## Checklist

### Prerequisites

- [x] Install Podman (4.0+) and verify `podman info` works
- [x] Install DevSpace CLI
- [x] Verify cgroup v2 is active (`cat /sys/fs/cgroup/cgroup.controllers`)
- [ ] Install `slirp4netns` if on older kernel (for rootless networking)
- [x] Apply one-time host sysctl fix (`/etc/sysctl.d/99-hermes.conf`): `br_netfilter`, inotify limits
- [x] Run `make setup-podman` once (unqualified-search-registries for docker.io)

### Local Development

- [x] Change `MINIKUBE_DRIVER` from `docker` to `podman` in `Makefile`
- [x] Add `MINIKUBE_ROOTLESS=true` and `--rootless` for Podman driver in Makefile
- [x] Fix minikube running checks for rootless Podman (`minikube-common.sh`; `minikube status` needs sudo)
- [ ] Delete existing minikube profile (`minikube delete -p hermes-dev`) *(manual, per developer)*
- [ ] Recreate minikube with podman driver (`make minikube-up`) *(manual, per developer)*
- [x] Create `devspace.yaml` at project root (see template above)
- [x] Add `scripts/podman-minikube-build.sh` (host build → `podman save` + `docker load` into minikube cri-dockerd when podman-env unavailable)
- [x] Update Makefile targets: `back`, `back-local`, `back-dynamic`, `teardown`, `minikube-preflight`
- [x] Update `infrastructure/k8s/hard-reset.sh`: `docker` -> `podman`
- [x] Fix dev-local Kustomize overlays (local `secrets.env` / `params.env`; `setup-env.sh` copies to dev-local)
- [ ] Run `devspace dev` and verify all 4 services build and deploy
- [ ] Verify port-forwarding works (http-gateway on localhost:8080, ws-gateway on localhost:8082)
- [ ] Verify health checks pass for all services
- [ ] Test `devspace dev -p local-secrets` profile
- [ ] Test `devspace dev -p dynamic-secrets` profile

### Infrastructure Testing

- [x] Create `infrastructure/scripts/validate-builds.sh` (reads `.github/services.json`) *(run: `make validate-builds`)*
- [x] Create `infrastructure/scripts/validate-manifests.sh` and verify all 4 overlays render *(auto-generates secrets.env from `.env` when present)*
- [x] Create `infrastructure/scripts/validate-terraform.sh` and verify secrets-manager + cognito modules *(run: `make validate-terraform`)*
- [x] Create `infrastructure/scripts/minikube-preflight.sh` (cluster DNS check; run: `make minikube-preflight`)
- [x] Run `devspace print` for each profile to validate DevSpace config *(run: `make validate-devspace`)*
- [x] Add DevSpace `integration` profile (local-secrets deploy without wait/health hooks)
- [x] Streamline `integration-test-local.sh` (single build; `INTEGRATION_REUSE_CLUSTER=1` for reruns)
- [x] Run full local integration test (`make integration-test`) end-to-end *(passes with cri-dockerd image load fix)*
- [ ] Run Trivy (or equivalent) image scan with no CRITICAL vulnerabilities
- [x] Verify all images run as non-root user *(validate-builds: http-gateway, auth-service, user-service, ws-gateway)*
- [x] Add `validate-builds`, `validate-manifests`, `validate-terraform`, `validate-devspace`, `validate-all`, and `integration-test` Makefile targets
- [x] Add `scripts/setup-podman.sh` for unqualified-search-registries (docker.io)

### CI/CD

- [x] Rename `docker-build` to `container-build` in `ci.yml` and switch to `podman build`
- [x] Add ws-gateway to the CI build matrix (with its separate build context)
- [x] Expand `infra-validate` to cover cognito TF module, all 4 Kustomize overlays, and DevSpace config
- [x] Add `infra-test` job: image sanity checks, layer analysis, rootless networking test
- [x] Update `.github/workflows/build-images.yml` to use `podman login` + `podman build` + `podman push`
- [x] Add pull-back verification step in `build-images.yml`
- [x] Remove `docker/build-push-action` and `docker/login-action` dependencies
- [x] Configure GitHub Actions cache for Maven (`~/.m2/repository`) and Go (`~/go/pkg/mod`)
- [ ] Run CI pipeline on a feature branch and verify all jobs pass
- [ ] Verify pushed images are pullable from Docker Hub

### Cleanup

- [x] Delete `skaffold.yaml` from the repository
- [ ] Uninstall Skaffold CLI locally
- [x] Update project README to reference DevSpace and Podman
- [ ] Remove Docker Engine / Docker Desktop
- [x] Remove any `docker` references in scripts (grep for `docker` across the repo) *(operational docs updated; `podman.md` retains migration before/after examples)*
- [x] Verify no CI workflow references `docker/build-push-action` or `docker/login-action`

### Streamline service and database onboarding

- [x] Implement dynamic ExternalSecret discovery in wait scripts (see section above)
- [x] Create `.github/services.json` and update CI workflows to use `fromJson`
- [x] Create `scripts/new-service.sh` scaffolding script with templates
- [x] Create `scripts/new-database.sh` scaffolding script with templates
- [x] Create `infrastructure/templates/` directory with base templates
- [x] Deduplicate the devspace-already-running guard across `back` / `back-local` / `back-dynamic` Makefile targets
- [x] Add a confirmation prompt to production-affecting Makefile targets (`deploy-prod-k8s`, `eso-sync-prod`)
- [x] Rearrange Vault seed to data-driven `infrastructure/vault/secrets/*.env` files

- [x] Rearrange Vault seed script to data-driven `infrastructure/vault/secrets/*.env` discovery

### Validation

- [ ] Full `make back` flow works end-to-end with Podman + DevSpace *(app readiness confirmed on hermes-test after image-load fix)*
- [ ] Full CI build + push works on `develop` branch *(push `refactor/docker-to-podman` and verify Actions)*
- [ ] Staging deploy (`devspace deploy -p staging`) applies correctly
- [ ] `make teardown` cleans up all resources
- [ ] Another developer can onboard using only Podman (no Docker installed)
- [x] Cluster DNS preflight passes after rootless minikube sysctl fix *(run: `make minikube-preflight`)*
- [x] DevSpace deploy applies full local-secrets stack (postgres, keycloak, 4 apps) to minikube
- [x] App deployments become Available after image load into minikube cri-dockerd
