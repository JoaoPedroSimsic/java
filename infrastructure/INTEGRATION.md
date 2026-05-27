# Application and platform secret integration

How Hermes workloads consume secrets after Phase 2 (Vault + ESO dev) and Phase 3 (AWS SM + ESO staging/prod).

## Consumption pattern

All workloads use the same Kubernetes pattern:

| Source | Kustomize generator | Mounted via | Contents |
|--------|---------------------|-------------|----------|
| Non-secret config | `configMapGenerator` → `params.env` | `envFrom.configMapRef` | Hosts, ports, profiles, public OAuth client IDs |
| Secrets | ESO → `ExternalSecret` → K8s `Secret` | `envFrom.secretRef` or `valueFrom.secretKeyRef` | Passwords, signing keys, OAuth client secrets |

**Spring Boot** and **Go (ws-gateway)** both read configuration from **environment variables** injected by Kubernetes. No file-based secret volumes in phase 1.

### Fail-closed startup

Base `deployment.yaml` files wire sensitive values through `secretRef` / `secretKeyRef` only — never as plain `env` literals in overlays. If ESO has not synced yet and the target `Secret` is missing, the Pod stays in **`CreateContainerConfigError`** instead of starting with empty credentials.

Init containers that need credentials (Postgres `pg_isready`, RabbitMQ management health) use explicit `secretKeyRef` entries pointing at the same ESO-managed secrets.

**ws-gateway** is an exception: it has no secrets today. Dev/staging/prod overlays patch out `secretRef` so the pod is not blocked by a non-existent `ws-gateway-secrets` object.

**Vault Agent Injector** is not used (phase 1). Revisit in Phase D if file-based injection is required.

## Spring Boot services

| Service | Profile (dev) | Profile (staging/prod) | ConfigMap (`*-config`) | Secret (`*-secrets`) |
|---------|---------------|------------------------|------------------------|----------------------|
| auth-service | `dev` → Keycloak IdP | `prod` → Cognito IdP | `AUTH_*`, `KC_*`, `KEYCLOAK_*` (non-secret), cookie tuning | DB, Flyway, RabbitMQ, `GITHUB_CLIENT_SECRET`, Keycloak admin, `COGNITO_CLIENT_SECRET` |
| user-service | `dev` | `prod` | `USER_*`, DB hosts | `GATEWAY_SECRET`, DB, Flyway, RabbitMQ |
| http-gateway | `dev` → Keycloak JWKS | `prod` → Cognito JWKS | Rate limits, service hosts, `KC_*` or `COGNITO_*` URLs | `GATEWAY_SECRET` |

Property binding uses relaxed env names (e.g. `GATEWAY_SECRET` → `gateway.secret` / `app.jwt.secret`).

### Identity provider credential flow

| Environment | auth-service IdP | JWT validation (http-gateway, ws-gateway) | Shared secrets |
|-------------|------------------|-------------------------------------------|----------------|
| **dev** | Keycloak admin API (`KEYCLOAK_ADMIN*` from secret; realm config in ConfigMap) | Keycloak JWKS (`KC_JWKS_URL`, `KC_JWT_ISSUER`) | `jwt-signing-key` → `GATEWAY_SECRET` |
| **staging / prod** | AWS Cognito (`COGNITO_*` IDs in ConfigMap; `COGNITO_CLIENT_SECRET` in secret) | Cognito JWKS (`COGNITO_JWKS_URL`, `COGNITO_JWT_ISSUER`) | Same shared JWT key path |

`GATEWAY_SECRET` is the **Hermes-issued JWT** signing key (user-service + http-gateway). It is **not** the Cognito or Keycloak IdP secret — those are separate integration credentials.

`KeycloakProperties` is `@Profile("dev")`; `CognitoProperties` is `@Profile("prod")` so only the active IdP config is validated at startup.

### Actuator hardening

`/actuator/env` is **disabled** in all profiles. `configprops` and `beans` endpoints hide property values (`show-values: never`). Dev profiles may expose additional endpoints (metrics, conditions) but never raw env values.

Health probes use `/actuator/health` with `show-details: never` (or `when_authorized` in dev) so responses do not leak connection strings or credentials.

## Platform components (phase 1 — static credentials)

| Component | ConfigMap | Secret (ESO) |
|-----------|-----------|--------------|
| auth-db / user-db Postgres | `POSTGRES_DB`, `POSTGRES_PORT` | `POSTGRES_USER`, `POSTGRES_PASSWORD`, `APP_*`, `FLYWAY_*` |
| RabbitMQ | — | `RABBITMQ_DEFAULT_USER/PASS`, `RABBITMQ_USERNAME/PASSWORD` |
| Keycloak | `GITHUB_CLIENT_ID`, `POSTGRES_DB` | `KEYCLOAK_ADMIN*`, `GITHUB_CLIENT_SECRET` |

Dynamic credentials (Vault database engine, short-lived Keycloak admin tokens) are deferred to Phase D.

## Regenerating overlay config

```bash
cd infrastructure/k8s
./setup-env.sh dev          # Vault + ESO path (params.env only)
./setup-env.sh staging      # AWS SM + ESO
./setup-env.sh prod
./setup-env.sh dev --local-secrets   # offline fallback with secrets.env
```

See also: `infrastructure/vault/README.md`, `infrastructure/terraform/secrets-manager/README.md`, `infrastructure/k8s/secrets.env.example`.
