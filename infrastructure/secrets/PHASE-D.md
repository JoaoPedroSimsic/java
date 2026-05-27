# Phase D — dynamic secrets, automatic rotation, CI scanning (PRD §7)

Optional hardening beyond static KV / AWS SM secrets. Phase D adds **automatic AWS rotation**, **Vault database static roles (dev)**, and completes **CI secret scanning**.

## 1. Secret scanning in CI (complete)

| Tool | Local | CI |
|------|-------|-----|
| gitleaks | pre-commit | `.github/workflows/ci.yml` `secret-scan` job |
| detect-secrets | pre-commit + `.secrets.baseline` | same job |

Install: `pip install pre-commit && pre-commit install`

## 2. AWS Secrets Manager — automatic rotation

Generic **four-step rotation Lambda** rotates JSON fields in AWS SM without updating downstream systems. After rotation:

1. ESO refreshes Kubernetes `Secret` objects (`refreshInterval` or `force-sync` annotation).
2. Operators run coordinated workload restarts ([ROTATION.md](../terraform/secrets-manager/ROTATION.md)).

### Enable rotation (staging / prod)

```bash
cd infrastructure/terraform/secrets-manager
# In terraform.tfvars:
#   enable_automatic_rotation = true
#   rotation_schedule_days    = 90
# Optional SNS:
#   rotation_notification_sns_topic_arn = "arn:aws:sns:..."

./secrets-manager-tf.sh apply
```

### Rotated secrets (default)

| Logical key | AWS suffix | Rotated JSON field |
|-------------|------------|-------------------|
| `jwt_signing_key` | `shared/jwt-signing-key` | `value` |
| `rabbitmq` | `shared/rabbitmq` | `password` |
| `keycloak_admin` | `services/keycloak/keycloak-admin` | `password` |
| `auth_service_cognito` | `services/auth-service/cognito` | `client_secret` |

**Excluded:** Postgres JSON bundles (`auth-db/postgres`, `user-db/postgres`) — require DB-aware rotators (future).

**Post-rotation runbook:**

```bash
export HERMES_ENV=prod
export NAMESPACE=hermes-${HERMES_ENV}
# Force ESO sync (see ROTATION.md)
kubectl rollout restart deployment/user-service deployment/http-gateway deployment/auth-service -n "$NAMESPACE"
```

Lambda source: [`infrastructure/aws/lambda/secrets-rotation/handler.py`](../aws/lambda/secrets-rotation/handler.py)

## 3. Vault database static roles (dev opt-in)

Rotates **auth-db `APP_USER` password** in Postgres on a schedule while keeping a fixed username. ESO merges dynamic creds into `auth-service-secrets` via `creationPolicy: Merge`.

### Prerequisites

- Default dev path working (`make back` or `make vault-init` + `make eso-sync`)
- auth-db Postgres running with users from KV seed (`make vault-seed`)

### Enable

```bash
make vault-database-engine          # bootstrap database engine + static role
make eso-sync-dynamic               # deploy dev-dynamic ExternalSecrets
make back-dynamic                   # full stack with Skaffold profile dynamic-secrets
```

Or stepwise:

```bash
make vault-init
make vault-database-engine
make eso-sync-dynamic
MINIKUBE_PROFILE=hermes-dev skaffold dev -p dynamic-secrets
```

### Components

| Piece | Location |
|-------|----------|
| Bootstrap script | `infrastructure/vault/scripts/bootstrap-dev-database-engine.sh` |
| Database ClusterSecretStore | `infrastructure/k8s/shared/external-secrets/config/dev/cluster-secret-store-database.yaml` |
| Merge ExternalSecret | `infrastructure/k8s/shared/external-secrets/manifests/dev-dynamic/auth-service-app-dynamic.yaml` |
| Policy (database read) | `infrastructure/vault/policies/hermes-apps-read.hcl` |

Static role path: `database/static-creds/hermes-auth-app-static` (default 24h rotation).

### Verify

```bash
kubectl get externalsecret auth-service-app-dynamic -n hermes-dev
kubectl get secret auth-service-secrets -n hermes-dev -o jsonpath='{.data.APP_USER}' | base64 -d; echo
```

## 4. Not in Phase D (future)

- Vault Agent Injector / CSI file mounts
- Vault Keycloak secrets engine (short-lived admin tokens)
- RabbitMQ / Keycloak-aware rotation Lambdas (require VPC + API calls)
- Postgres bundle auto-rotation in AWS SM
- JWT dual-key validation without coordinated restart

## Related docs

- [ROTATION.md](../terraform/secrets-manager/ROTATION.md) — manual + automatic rotation
- [INTEGRATION.md](../INTEGRATION.md) — consumption patterns
- [SECURITY.md](SECURITY.md) — guardrails
