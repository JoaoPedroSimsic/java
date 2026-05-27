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

### CI/CD

- [ ] Update `.github/workflows/ci.yml` `docker-build` job to use `podman build`
- [ ] Update `.github/workflows/build-images.yml` to use `podman login` + `podman build` + `podman push`
- [ ] Remove `docker/build-push-action` and `docker/login-action` dependencies
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
