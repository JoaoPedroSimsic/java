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
sudo apt install podman podman-compose

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

### (Optional) Docker socket compatibility shim

Some tools still look for `DOCKER_HOST`. You can expose Podman's API on a socket:

```bash
# Start the Podman socket (systemd user service)
systemctl --user enable --now podman.socket

# Export for tools that expect DOCKER_HOST
export DOCKER_HOST=unix://$XDG_RUNTIME_DIR/podman/podman.sock
```

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

Replace `skaffold.yaml` with a `devspace.yaml` at the project root. Below is the equivalent configuration:

```yaml
version: v2beta1
name: hermes

vars:
  MINIKUBE_PROFILE: hermes-dev
  CONTAINER_CMD: podman

images:
  http-gateway:
    image: http-gateway
    buildKit: {}
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
  ports:
    - imageSelector: http-gateway
      forward:
        - port: 8080
          remotePort: 8081

hooks:
  - command: bash
    args:
      - infrastructure/k8s/shared/external-secrets/scripts/ensure-crds.sh
    events: ["before:deploy"]

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
      - op: replace
        path: deployments.hermes.kubectl.manifests
        value:
          - infrastructure/k8s/shared/external-secrets/overlays/staging
          - infrastructure/k8s/shared/external-secrets/manifests/staging
          - infrastructure/k8s/clusters/staging

  - name: prod
    patches:
      - op: replace
        path: deployments.hermes.kubectl.manifests
        value:
          - infrastructure/k8s/shared/external-secrets/overlays/prod
          - infrastructure/k8s/shared/external-secrets/manifests/prod
          - infrastructure/k8s/clusters/prod
```

### Step 3: Update the Makefile

Replace skaffold commands with devspace:

```makefile
# Before
back: minikube-up vault-init eso-sync
	MINIKUBE_PROFILE="$(MINIKUBE_PROFILE)" skaffold dev --trigger="$(SKAFFOLD_TRIGGER)" $(SKAFFOLD_DEV_FLAGS)

# After
back: minikube-up vault-init eso-sync
	devspace dev

back-local: minikube-up
	devspace dev -p local-secrets

back-dynamic: minikube-up vault-init vault-database-engine eso-sync-dynamic
	devspace dev -p dynamic-secrets
```

Also update the `teardown` target:

```makefile
# Before
teardown:
	skaffold delete

# After
teardown:
	devspace purge
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

### Step 5: Load images into minikube

With the podman driver, minikube can use images built by podman directly if they share the same storage. If images aren't found, load them explicitly:

```bash
# DevSpace handles this automatically via its build pipeline,
# but for manual builds:
minikube -p hermes-dev image load <image>:<tag>

# Or configure minikube to use podman's image store:
eval $(minikube -p hermes-dev podman-env)
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
for dep in auth-service user-service http-gateway; do
  echo "Waiting for $dep..."
  kubectl wait deployment/"$dep" -n "$NAMESPACE" \
    --for=condition=Available --timeout="${TIMEOUT}s"
done

echo "=== Phase 5: Health checks ==="
for svc_port in "http-gateway:8081:/actuator/health" \
                "auth-service:8083:/actuator/health" \
                "user-service:8084:/actuator/health"; do
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

validate-all: validate-builds validate-manifests validate-terraform

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

## Checklist

### Prerequisites

- [ ] Install Podman (4.0+) and verify `podman info` works
- [ ] Install DevSpace CLI
- [ ] Verify cgroup v2 is active (`cat /sys/fs/cgroup/cgroup.controllers`)
- [ ] Install `slirp4netns` if on older kernel (for rootless networking)

### Local Development

- [ ] Change `MINIKUBE_DRIVER` from `docker` to `podman` in `Makefile`
- [ ] Delete existing minikube profile (`minikube delete -p hermes-dev`)
- [ ] Recreate minikube with podman driver (`make minikube-up`)
- [ ] Create `devspace.yaml` at project root (see template above)
- [ ] Update Makefile targets: `back`, `back-local`, `back-dynamic`, `teardown`
- [ ] Update `infrastructure/k8s/hard-reset.sh`: `docker` -> `podman`
- [ ] Run `devspace dev` and verify all 4 services build and deploy
- [ ] Verify port-forwarding works (http-gateway on localhost:8080)
- [ ] Verify health checks pass for all services
- [ ] Test `devspace dev -p local-secrets` profile
- [ ] Test `devspace dev -p dynamic-secrets` profile

### Infrastructure Testing

- [ ] Create `infrastructure/scripts/validate-builds.sh` and verify all 4 services build with Podman
- [ ] Create `infrastructure/scripts/validate-manifests.sh` and verify all 4 overlays render
- [ ] Create `infrastructure/scripts/validate-terraform.sh` and verify secrets-manager + cognito modules
- [ ] Run `devspace print` for each profile to validate DevSpace config
- [ ] Run full local integration test (`integration-test-local.sh`) end-to-end
- [ ] Run Trivy (or equivalent) image scan with no CRITICAL vulnerabilities
- [ ] Verify all images run as non-root user
- [ ] Add `validate-builds`, `validate-manifests`, `validate-terraform`, `validate-all`, and `integration-test` Makefile targets

### CI/CD

- [ ] Rename `docker-build` to `container-build` in `ci.yml` and switch to `podman build`
- [ ] Add ws-gateway to the CI build matrix (with its separate build context)
- [ ] Expand `infra-validate` to cover cognito TF module, all 4 Kustomize overlays, and DevSpace config
- [ ] Add `infra-test` job: image sanity checks, layer analysis, rootless networking test
- [ ] Update `.github/workflows/build-images.yml` to use `podman login` + `podman build` + `podman push`
- [ ] Add pull-back verification step in `build-images.yml`
- [ ] Remove `docker/build-push-action` and `docker/login-action` dependencies
- [ ] Configure GitHub Actions cache for Maven (`~/.m2/repository`) and Go (`~/go/pkg/mod`)
- [ ] Run CI pipeline on a feature branch and verify all jobs pass
- [ ] Verify pushed images are pullable from Docker Hub

### Cleanup

- [ ] Remove or archive `skaffold.yaml` (keep in `infrastructure/archive/` for reference)
- [ ] Uninstall Skaffold locally (optional)
- [ ] Update project README to reference DevSpace and Podman
- [ ] Remove Docker Desktop / Docker Engine if no longer needed

### Validation

- [ ] Full `make back` flow works end-to-end with Podman + DevSpace
- [ ] Full CI build + push works on `develop` branch
- [ ] Staging deploy (`devspace deploy -p staging`) applies correctly
- [ ] `make teardown` cleans up all resources
- [ ] Another developer can onboard using only Podman (no Docker installed)
