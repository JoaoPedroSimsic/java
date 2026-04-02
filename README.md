# Hermes

Hermes is a multi-service platform: a Spring Cloud HTTP API gateway, dedicated auth and user backends, a Go WebSocket gateway backed by NATS, and an Angular web app. Infrastructure is defined as Kubernetes manifests (Kustomize) with optional Skaffold-based local workflows.

## What’s in this repository

| Area | Description |
|------|-------------|
| **gateways/http-gateway** | Spring Cloud Gateway — routing, Redis, JWT handling |
| **gateways/ws-gateway** | Go — WebSockets, JWT, NATS, Redis |
| **services/auth-service** | Spring Boot — authentication, Keycloak/Cognito integration, outbox to RabbitMQ |
| **services/user-service** | Spring Boot — users and profiles, AWS integration, messaging |
| **events** | Shared JSON Schema → Java models (jsonschema2pojo) for cross-service events |
| **frontend** | Angular 21 SPA (package manager: Bun) |
| **infrastructure/k8s** | Kustomize layouts for dev/staging/prod (Postgres, Redis, RabbitMQ, Keycloak, NATS, ingress, observability) |
| **infrastructure/terraform**, **infrastructure/lambda** | Supporting cloud and automation pieces |

Java services use **Java 21** and **Spring Boot 3.4** (see `gateways/http-gateway/pom.xml`). The WebSocket gateway uses **Go 1.24** (`gateways/ws-gateway/go.mod`).

## Prerequisites

- **JDK 21** and **Maven** (or use `./mvnw` at the repo root)
- **Docker** and a **Kubernetes** cluster (e.g. minikube) for full-stack deployment
- **kubectl** and **kustomize** (or Skaffold — see below)
- **Bun** (recommended) or npm for the frontend — see `frontend/package.json` (`packageManager`)

## Local development

### Backend (Maven)

From the repository root:

```bash
./mvnw clean verify
```

This builds the BOM, `events`, gateways, and services. Individual modules can be built or run from their directories with the same wrapper.

### Frontend

```bash
cd frontend
bun install   # or: npm install
bun run start # or: npm run start — serves at http://localhost:4200
```

### Kubernetes (Skaffold)

The repo includes `skaffold.yaml` for building images and deploying the **dev** cluster overlay, with port forwarding for the HTTP gateway (e.g. local **8080** → gateway **8081** in `hermes-dev`). Use a profile for production-shaped deploys:

```bash
skaffold dev
# or
skaffold run -p prod
```

Exact image names, namespaces, and health checks are defined in `skaffold.yaml`.

### Dev container

The `.devcontainer` setup installs frontend dependencies, wires Docker-on-host, and mounts your kubeconfig/minikube dirs for in-editor Kubernetes workflows. Open the repo in a Dev Containers–compatible editor to use it.

## Documentation

- **[infrastructure/k8s/README.md](infrastructure/k8s/README.md)** — cluster layout, secrets (`secrets.env` vs `secrets.env.example`), deploy commands, troubleshooting.
- **[frontend/README.md](frontend/README.md)** — Angular CLI usage (serve, build, test).

For deep operational detail (ingress, namespaces like `hermes-dev`, rotating secrets), start with the Kubernetes README above.
