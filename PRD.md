# Product requirements (Hermes)

## Secret management — implementation plan

**Objective:** Centralize application and platform secrets with **HashiCorp Vault in development** and **AWS Secrets Manager in production**, replacing or augmenting today’s **Kustomize `secretGenerator` + `secrets.env`** workflow where appropriate.

**Principles:** Least privilege, no long-lived secrets in git, auditable access, environment parity for developers without copying prod credentials locally.

---

### 1. Discovery and standards

- [x] Inventory all current secrets across **multiple data stores** (e.g. auth-db, user-db, auth-redis, gateway-cache Postgres/Redis instances, RabbitMQ, Keycloak, NATS, app env, TLS material) and map each to owning service and Kustomize overlay.
- [x] Define a **secret naming convention** for Vault paths and AWS Secrets Manager names/ARNs that explicitly distinguishes:
  - [x] **Shared secrets** — consumed by more than one workload (e.g. a single **JWT signing key** used by `http-gateway` and `user-service` for `APP_JWT_SECRET` / `GATEWAY_SECRET`-style configuration). Example path shape: `hermes/<env>/shared/jwt-signing-key`.
  - [x] **Service-specific secrets** — scoped to one bounded context (e.g. auth-db Postgres password, user-db password, per-service Redis passwords).
- [x] Document **classification** (PII, credentials, TLS keys) and required **rotation** cadence per secret type.
- [x] Agree on **bootstrap secrets** that may remain local-only in dev (e.g. minikube) vs must always come from Vault.

#### 1.1 Inventory (repository state)

Sources are **`secretGenerator` + `secrets.env`** per overlay (see `infrastructure/k8s/.gitignore`; real values are not committed). **`secrets.env.example`** is the committed shape reference.

| Data store / area | Sensitive material | Kubernetes `Secret` name (from Kustomize) | Overlay path(s) | Consuming workload(s) |
|-------------------|---------------------|-------------------------------------------|-----------------|-------------------------|
| **auth-db** (Postgres) | `POSTGRES_PASSWORD`, `FLYWAY_PASSWORD`, `APP_PASSWORD` (+ users in env) | `auth-postgres-secrets` | `shared/postgres/auth-db/overlays/{dev,staging,prod}` | Postgres StatefulSet/init; **auth-service** uses `APP_PASSWORD` / flyway via `auth-service-secrets` (must match `APP_PASSWORD` / `FLYWAY_PASSWORD`) |
| **user-db** (Postgres) | Same key pattern as auth-db | `user-postgres-secrets` | `shared/postgres/user-db/overlays/{dev,staging,prod}` | user-db; **user-service** via `user-service-secrets` |
| **auth-redis** | *None today* — Redis runs without `requirepass` in manifests | — | `shared/redis/auth-redis/overlays/*` | **auth-service** (cache only) |
| **user-redis** | *None today* | — | `shared/redis/user-redis/overlays/*` | **user-service** |
| **gateway-cache** | *None today* | — | `shared/redis/gateway-cache/overlays/*` | **http-gateway**, **ws-gateway** (rate limiting / validation context) |
| **RabbitMQ** | `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD` | `rabbitmq-secrets` | `shared/rabbitmq/overlays/{dev,staging,prod}` | RabbitMQ deployment; **auth-service**; **http-gateway** initContainer (management API) |
| **Keycloak** | `KEYCLOAK_ADMIN`, `KEYCLOAK_ADMIN_PASSWORD`, `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET` | `keycloak-secrets` | `shared/keycloak/overlays/{dev,staging,prod}` | Keycloak; **auth-service** (admin API / GitHub OAuth via app config) |
| **NATS** | *None in manifests* (no auth args) | — | `shared/nats/overlays/*` | **ws-gateway** (client to `nats://…`) |
| **http-gateway** | `GATEWAY_SECRET` (JWT HMAC; must match user-service `app.jwt.secret`) | `gateway-secrets` | `gateways/http-gateway/overlays/{dev,staging,prod}` | **http-gateway** |
| **user-service** | `GATEWAY_SECRET`, DB + flyway + RabbitMQ passwords; prod adds **AWS** `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` (see `application-prod.yml`) | `user-service-secrets` | `services/user-service/overlays/{dev,staging,prod}` | **user-service** |
| **auth-service** | DB + flyway + RabbitMQ + Keycloak admin + GitHub secret + cookie/token tuning envs as in overlay | `auth-service-secrets` | `services/auth-service/overlays/{dev,staging,prod}` | **auth-service** |
| **ws-gateway** | `secretGenerator` references `secrets.env` (often **local-only**, gitignored); **no keys required in code today** (JWT via JWKS). Prod profile may need Cognito-related env in secrets later. | `ws-gateway-secrets` | `gateways/ws-gateway/overlays/{dev,staging,prod}` | **ws-gateway** |
| **TLS / Ingress** | *None in current ingress YAML* (HTTP-only rules in base) | — | `shared/ingress/overlays/*` | — |

**Cross-cutting:** `GATEWAY_SECRET` is the **shared JWT signing material** for `user-service` (`app.jwt.secret`) and `http-gateway` (`gateway.secret`). Align values across `user-service-secrets` and `gateway-secrets`.

**Production-only (not in dev Kustomize secrets files today):** Cognito identifiers and `COGNITO_CLIENT_SECRET` for **auth-service** (`application-prod.yml`); static Keycloak admin password placeholder in prod profile should be reconciled with secret management in a later rollout phase.

#### 1.2 Naming convention (Vault and AWS Secrets Manager)

Use the same **logical hierarchy** in both systems; only the prefix/mount differs.

- **`env`**: `dev` | `staging` | `prod` (and future regions/accounts as needed).

**Vault (KV v2 logical paths)** — after mount (e.g. mount `secret` → full API path `secret/data/hermes/...`):

| Kind | Path pattern | Example |
|------|----------------|---------|
| Shared | `hermes/<env>/shared/<name>` | `hermes/dev/shared/jwt-signing-key` |
| Service-specific | `hermes/<env>/services/<service>/<name>` | `hermes/prod/services/auth-db/postgres-users` |

- **Shared** names to use for this codebase: `jwt-signing-key` (maps to env `GATEWAY_SECRET`), `rabbitmq` (broker user/pass), `github-oauth` (Keycloak IdP), etc., as needed.
- **Service-specific** examples: `postgres` (bundle of `POSTGRES_PASSWORD`, `APP_PASSWORD`, `FLYWAY_PASSWORD` as JSON keys), `keycloak-admin`, `cognito` (prod).

**AWS Secrets Manager** — secret **name** (allows `/`):

- Shared: `hermes/<env>/shared/<name>` (e.g. `hermes/prod/shared/jwt-signing-key`).
- Service-specific: `hermes/<env>/services/<service>/<name>` (e.g. `hermes/prod/services/user-service/aws-s3-credentials`).

ARNs follow `arn:aws:secretsmanager:<region>:<account>:secret:hermes/<env>/...` with the service-supplied suffix.

#### 1.3 Classification and rotation cadence

| Category | Examples in Hermes | Classification | Rotation cadence (target) |
|----------|---------------------|----------------|---------------------------|
| Symmetric signing / HMAC | `GATEWAY_SECRET` | Credential (cryptographic) | 90 days or on incident; coordinated rotation across http-gateway + user-service |
| Database passwords | Postgres `APP_PASSWORD`, `FLYWAY_PASSWORD`, `POSTGRES_PASSWORD` | Credential | 90 days; flyway + app coordinated |
| Message broker | RabbitMQ user/password | Credential | 90 days |
| Identity admin / IdP | Keycloak admin, GitHub OAuth client secret | Credential; GitHub secret also **third-party OAuth** | 90 days admin; rotate OAuth when GitHub rotates |
| Cloud IAM keys | `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` (user-service prod) | Credential | Prefer **IRSA / workload identity** and eliminate long-lived keys; else 90 days |
| Cognito | `COGNITO_CLIENT_SECRET` | Credential | Per AWS / app rotation policy |
| PII | *(none stored as “secrets” in env files)* | PII belongs in app data stores, not in secret blobs | N/A |
| TLS private keys | *(not present in current k8s manifests)* | Credential | Per PKI / ACME (e.g. annual or per cert lifetime) |

#### 1.4 Bootstrap vs central secrets store

| Context | Allowed | Must come from Vault (dev) / AWS SM + ESO (staging/prod) |
|---------|---------|-----------------------------------------------------------|
| **Local dev / minikube** | `secrets.env` + `secretGenerator` for all of §1.1; optional **pure offline** copy from `secrets.env.example` | N/A for prod-like guarantees |
| **Shared dev clusters** | Same as local if policy allows; prefer Vault when available | JWT signing key, DB passwords, broker, Keycloak, OAuth |
| **Staging / production** | Emergency break-glass procedures only (documented out of band) | **All** credentials and signing keys; no committed plaintext |

**Vault bootstrap (for operators / ESO):** initial auth method setup, one-time tokens, and namespace-bound Kubernetes auth roles are **bootstrap** artifacts — store **outside git** (local files, CI OIDC, or cloud provider secret), consistent with §2 of this plan.

---

### 2. Development — HashiCorp Vault

- [x] Choose deployment model for dev: **Vault in Kubernetes** (minikube); document ports, unseal strategy, and data persistence expectations.
- [x] Add **Vault Helm chart** or **official container** to `infrastructure/` (dev overlay only), with resource limits suitable for local clusters.
- [x] Configure **KV secrets engine** (v2) and optional **database / RabbitMQ** engines if dynamic credentials are in scope for a later phase.
- [x] **Keycloak (dev):** Evaluate the **Vault Keycloak secrets engine** to issue **short-lived admin credentials** for Keycloak integration (e.g. `KeycloakAdapter`) instead of long-lived `KEYCLOAK_ADMIN_PASSWORD` values in `secrets.env`; document trade-offs vs static KV for phase 1.
- [x] Define **Vault policies** per role (read-only for apps, admin for bootstrap) and **authentication**: Kubernetes auth (preferred for in-cluster workloads) and **token/AppRole** for local scripts.
- [x] **Bootstrap credentials for operators (dev):** Document how **ESO** and/or **Vault Agent** first authenticate to Vault in minikube:
  - [x] Add a **documented manual or scripted step** (e.g. in `bootstrap-dev-k8s-auth.sh`, `setup-env.sh`, or equivalent) to enable the **Kubernetes auth method** in Vault, configure roles, and **bind the namespace default ServiceAccount (or dedicated SA)** to the correct Vault policy so sync/injection can run before app pods start.
  - [x] Avoid checking long-lived root tokens into git; use one-time bootstrap tokens or local-only files excluded by `.gitignore`.
- [x] Integrate workloads using one of:
  - [x] **External Secrets Operator** (ESO) + Vault backend → syncs to Kubernetes `Secret` objects, or
  - [x] **Vault Agent Injector** / CSI provider — **not adopted** in phase 1 (ESO chosen per open decisions; Vault chart `injector.enabled: false`; revisit in Phase D if file-based injection is required).
- [x] Replace or gate **`secrets.env` + `secretGenerator`** in **dev** overlays: either generate K8s secrets from Vault in CI/CD, or reference ESO-managed secrets in Deployments.
- [x] **Offline / flaky network:** Provide a **local mock or fallback path** so `make back` is not blocked when minikube Vault is unreachable (e.g. optional overlay that keeps `secretGenerator` from `secrets.env`, or a documented **pure local** flow using **`secrets.env.example`** as a template for non-K8s development). Clearly label fallbacks as **non-prod** and unsafe for shared environments.
- [x] Update **Skaffold / Makefile** docs so `make back` brings up Vault (or depends on it) when using the default path, and developers know how to log in and read secrets.
- [x] Add **runbooks**: unseal (if applicable), root token handling, and “break-glass” local override for offline work.

#### 2.1 Implementation status (complete)

| Deliverable | Location |
|-------------|----------|
| Vault Helm (dev server, in-memory, auto-unsealed) | `infrastructure/k8s/shared/vault/overlays/dev` |
| KV v2 bootstrap + policies | `infrastructure/vault/policies/`, `bootstrap-dev-k8s-auth.sh` |
| Seed from `.env` | `infrastructure/vault/scripts/seed-dev-secrets.sh`, `make vault-seed` |
| ESO controller + ClusterSecretStore | `infrastructure/k8s/shared/external-secrets/overlays/dev`, `config/dev/` |
| ExternalSecret manifests (7 secrets) | `infrastructure/k8s/shared/external-secrets/manifests/dev/` |
| Dev default workflow | `make back` → `vault-init` + `eso-sync` + Skaffold |
| Offline fallback (non-prod) | `setup-env.sh dev --local-secrets`, `make back-local`, `clusters/dev-local-secrets` |
| Runbooks | `infrastructure/vault/README.md` |

**Integration path:** ESO syncs Vault KV into the Kubernetes `Secret` names Deployments already reference (`gateway-secrets`, `auth-service-secrets`, …). Dev overlays no longer use Kustomize `secretGenerator`; staging/prod use AWS Secrets Manager + ESO (Phase 3).

---

### 3. Production — AWS Secrets Manager

- [x] Create **AWS Secrets Manager** secrets per environment (e.g. `hermes/prod/...`) matching the naming convention from §1 (including **shared** vs **service-specific** paths).
- [x] **Bootstrap credentials for operators (prod):** Use **IAM Roles for Service Accounts (IRSA)** so **ESO** (and any AWS Secrets Store CSI driver) assumes least-privilege IAM roles to read only the secrets needed for each namespace/workload — no static AWS keys in the cluster for sync.
- [x] Configure workload **IRSA** where applications call AWS APIs directly (if any); keep ESO controller identity separate from app identity unless deliberately shared.
- [x] Deploy **External Secrets Operator** (or AWS-native **Secrets Store CSI + ASCP**) in prod and wire the **AWS provider** to Secrets Manager.
- [x] Map **ExternalSecret** resources to Kubernetes `Secret` names expected by existing Deployments, or update manifests to match new names.
- [x] Remove plaintext **`secrets.env` from prod pipelines**; ensure CI applies only references (and uses OIDC/IAM to push or rotate secrets, not store them in repo).
- [x] Enable **CloudTrail** logging for `secretsmanager` API calls and define alerts for anomalous access.

#### 3.1 Implementation status (complete)

| Deliverable | Location |
|-------------|----------|
| Terraform: Secrets Manager + IRSA + CloudTrail | `infrastructure/terraform/secrets-manager/` |
| Seed from `.env.<env>` | `infrastructure/aws/scripts/seed-secrets.sh`, `make aws-secrets-seed` |
| ESO controller + ClusterSecretStore (AWS) | `infrastructure/k8s/shared/external-secrets/overlays/{staging,prod}/`, `config/{staging,prod}/` |
| ExternalSecret manifests (7 secrets / env) | `infrastructure/k8s/shared/external-secrets/manifests/{staging,prod}/` |
| Prod/staging overlays (no `secretGenerator`) | `infrastructure/k8s/**/overlays/{staging,prod}/` |
| user-service IRSA (S3; no static AWS keys) | `services/user-service/overlays/prod/serviceaccount.yaml`, `application-prod.yml` |
| Deploy workflow | `make eso-sync-prod`, `make deploy-prod-k8s`; `.github/workflows/deploy-kubernetes.yml` |
| Runbook | `infrastructure/terraform/secrets-manager/README.md` |

**Integration path:** ESO syncs AWS Secrets Manager into the same Kubernetes `Secret` names as dev (`gateway-secrets`, `auth-service-secrets`, …). Staging and prod overlays no longer use Kustomize `secretGenerator`.

---

### 4. Application and platform integration

- [x] Standardize consumption: **env from projected secrets** vs **files**; align Spring Boot / Go configs with one pattern.
- [x] Refactor services to read **non-secret config from ConfigMaps** and **secrets only from Secret/volume** (no duplicate sensitive values in env files).
- [x] **Spring Boot “bootstrap” ordering:** Ensure **base `deployment.yaml` files** (and overlays) reference secrets via **`secretRef`** / **`valueFrom.secretKeyRef`** (not plain `env` literals for sensitive keys) so that if ESO has not yet synced from Vault/AWS, or a `Secret` is missing, the Pod remains in **`CreateContainerConfigError`** / **`ErrImagePull`-class failures** rather than starting with **null or empty env** and failing later in application code.
- [x] **Vault Agent Injector:** Not adopted in phase 1 (ESO only). Documented in `infrastructure/secrets/INTEGRATION.md`; revisit in Phase D.
- [x] **Authentication services:** Plan credential flow for **Keycloak** and **AWS Cognito** paths in `auth-service` (shared vs per-integration secrets; no accidental duplication of signing keys across IdPs unless intended).
- [x] Plan **Keycloak / Postgres / Redis / RabbitMQ** credential flow: static secrets in phase 1; optional **dynamic credentials** in a later iteration.
- [x] Add **health checks** that do not expose secret values in logs.
- [x] **Spring Boot Actuator:** Configure **`/actuator/env`** (and related endpoints) to **sanitize or disable** exposure of Vault- or AWS-sourced keys — default Spring behavior can leak secret **values** in JSON responses; restrict management endpoints by profile, network policy, or explicit property sanitizer lists.

#### 4.1 Implementation status (complete)

| Deliverable | Location |
|-------------|----------|
| Consumption pattern + fail-closed docs | `infrastructure/secrets/INTEGRATION.md` |
| ConfigMap vs Secret split (staging/prod `params.env`) | `infrastructure/k8s/**/overlays/{staging,prod}/params.env` |
| IdP properties scoped by profile | `KeycloakProperties` (`dev`), `CognitoProperties` (`prod`) |
| Actuator hardening (env disabled, values hidden) | `services/*/application*.yml`, `gateways/http-gateway/application*.yml` |
| Removed hardcoded prod Keycloak admin password | `services/auth-service/application-prod.yml` |
| `setup-env.sh` staging parity with prod Cognito vars | `infrastructure/k8s/setup-env.sh` |

**Integration path:** Non-secret env in `params.env` → ConfigMap; credentials in ESO-managed Secrets → `envFrom.secretRef`. Spring Boot and ws-gateway read env vars only (no secret files in phase 1).

---

### 5. Rotation, DR, and operations

- [x] Define **rotation procedure** for AWS Secrets Manager (manual + future Lambda/rotation rules).
- [x] Define **Vault** backup/rekey strategy for dev if Vault stores anything non-ephemeral.
- [x] Document **disaster recovery**: how to recreate secrets in a new account/region and reconnect clusters.

#### 5.1 Implementation status (complete)

| Deliverable | Location |
|-------------|----------|
| AWS SM manual rotation runbooks | `infrastructure/terraform/secrets-manager/ROTATION.md` |
| Vault dev backup/rekey (in-memory dev model) | `infrastructure/vault/README.md` (Operations section) |
| DR — AWS + dev Vault paths | `infrastructure/secrets/DR.md` |

**Integration path:** Coordinated JWT restart (user-service → http-gateway); ESO `force-sync` annotation; `make aws-secrets-seed` from break-glass `.env.<env>`. Automatic rotation via Terraform `enable_automatic_rotation` (Phase D).

---

### 6. Security, compliance, and testing

- [x] Enforce **no secret values in git**: extend `.gitignore` / pre-commit hooks for `secrets.env` and similar.
- [x] Add **smoke tests** in staging that verify workloads start with ESO/Vault-provided secrets only.
- [x] Security review: network policies for Vault, TLS for Vault API, and IAM policy boundaries for prod.

#### 6.1 Implementation status (complete)

| Deliverable | Location |
|-------------|----------|
| `.gitignore` extensions | repo root `.gitignore`, `infrastructure/k8s/.gitignore` |
| pre-commit (gitleaks + detect-secrets) | `.pre-commit-config.yaml`, `.secrets.baseline` |
| CI secret scan | `.github/workflows/ci.yml` `secret-scan` job |
| Staging smoke tests | `infrastructure/k8s/scripts/smoke-test-secrets.sh`, `make smoke-test-staging`, `deploy-kubernetes.yml` (staging) |
| Security review doc + Vault NetworkPolicy | `infrastructure/secrets/SECURITY.md`, `infrastructure/k8s/shared/vault/overlays/dev/network-policy.yaml` |

**Integration path:** Staging deploy workflow runs smoke tests after cluster apply. Dev Vault API restricted to ESO ingress on port 8200; TLS on Vault API documented as dev-only risk acceptance.

---

### 7. Rollout

- [x] **Phase A — Non-breaking:** Introduce Vault/AWS SM alongside existing `secretGenerator`; dual-write or read from new path with feature flag.
- [x] **Phase B:** Switch dev overlays to Vault-sourced secrets by default; keep **`secrets.env.example`** (or equivalent) for **documented variable names and shapes only** — no real values; support optional fallback flows from §2.
- [x] **Phase C:** Cut prod to AWS Secrets Manager + ESO; archive plaintext prod secret delivery paths.
- [x] **Phase D:** Optional — dynamic secrets, automatic rotation, and secret scanning in CI.

#### 7.1 Phase D implementation status (complete)

| Deliverable | Location |
|-------------|----------|
| CI secret scanning (gitleaks + detect-secrets) | `.pre-commit-config.yaml`, `.github/workflows/ci.yml`, `.secrets.baseline` |
| AWS SM automatic rotation Lambda + Terraform | `infrastructure/aws/lambda/secrets-rotation/`, `infrastructure/terraform/secrets-manager/rotation.tf` |
| Vault database static roles (dev opt-in) | `infrastructure/vault/scripts/bootstrap-dev-database-engine.sh`, `manifests/dev-dynamic/` |
| Skaffold / Makefile dynamic profile | `skaffold.yaml` profile `dynamic-secrets`, `make back-dynamic` |
| Documentation | `infrastructure/secrets/PHASE-D.md` |

**Integration path:** Rotation disabled by default (`enable_automatic_rotation = false`). Dev dynamic creds opt-in via `make back-dynamic`. Postgres bundles and IdP-aware rotators remain manual / future work.

---

### Open decisions (to resolve during implementation)

- [x] **Operator choice:** External Secrets Operator vs Vault Agent Injector vs mix (Vault in dev, ESO+AWS in prod is a common split).
- [x] **Staging environment:** Mirror prod (**AWS SM + ESO**); manifests under `infrastructure/k8s/shared/external-secrets/manifests/staging/`.
- [x] **Local developer laptop:** Minikube Vault via `make back` (default); `--local-secrets` / `make back-local` for offline fallback.
