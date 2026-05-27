# AWS Secrets Manager — rotation procedures (PRD §5)

Manual rotation playbooks for Hermes secrets in **staging** and **prod**. Target cadence follows PRD §1.3 (90 days for credentials, or immediately on incident).

Automatic Lambda rotation is **Phase D** — see [Future: automatic rotation](#future-automatic-rotation) below.

## Prerequisites

- AWS CLI authenticated with permission to `secretsmanager:PutSecretValue` on `hermes/<env>/*`
- `kubectl` access to the target EKS cluster and namespace `hermes-<env>`
- Break-glass values in `.env.<env>` (never committed) or your org's secret store

Set context:

```bash
export HERMES_ENV=prod   # or staging
export NAMESPACE="hermes-${HERMES_ENV}"
export AWS_REGION="${AWS_REGION:-sa-east-1}"
```

## General workflow

```text
1. Generate new credential(s)
2. Update AWS Secrets Manager (PutSecretValue or seed script)
3. Force ESO reconcile → wait for Kubernetes Secret update
4. Rolling restart affected workloads (order matters for some secrets)
5. Verify health + smoke tests
```

### Force ESO sync

After updating AWS SM, reconcile ExternalSecrets:

```bash
for es in gateway-secrets auth-service-secrets user-service-secrets \
          auth-postgres-secrets user-postgres-secrets rabbitmq-secrets keycloak-secrets; do
  kubectl annotate externalsecret/"$es" -n "$NAMESPACE" \
    force-sync="$(date +%s)" --overwrite
done
```

Or wait for the `refreshInterval` (default **1h** on ExternalSecret manifests).

### Update via seed script

When rotating from a local break-glass file:

```bash
HERMES_ENV=prod bash infrastructure/scripts/seed-secrets.sh
```

## Per-secret playbooks

### Shared JWT signing key (`hermes/<env>/shared/jwt-signing-key`)

**Maps to:** `GATEWAY_SECRET` in `gateway-secrets` and `user-service-secrets`.

**Coordination:** Both services must use the same key. Use a **coordinated restart** (no dual-key support in phase 1).

1. Generate a new strong random value (≥ 32 bytes).
2. Update AWS SM:

   ```bash
   aws secretsmanager put-secret-value \
     --secret-id "hermes/${HERMES_ENV}/shared/jwt-signing-key" \
     --secret-string "$(jq -nc --arg v "$NEW_GATEWAY_SECRET" '{value: $v}')" \
     --region "$AWS_REGION"
   ```

3. Force ESO sync for `gateway-secrets` and `user-service-secrets`.
4. Restart in order (minimize window where services disagree):

   ```bash
   kubectl rollout restart deployment/user-service -n "$NAMESPACE"
   kubectl rollout status deployment/user-service -n "$NAMESPACE" --timeout=300s
   kubectl rollout restart deployment/http-gateway -n "$NAMESPACE"
   kubectl rollout status deployment/http-gateway -n "$NAMESPACE" --timeout=300s
   ```

5. Verify: existing sessions may invalidate; new logins and API calls with fresh JWTs should succeed.

**Impact:** Brief auth disruption during restart; plan a maintenance window for prod.

### Postgres bundles (`hermes/<env>/services/{auth-db,user-db}/postgres`)

**Maps to:** `auth-postgres-secrets`, `user-postgres-secrets`, and app secrets (`APP_*`, `FLYWAY_*`).

All keys in the JSON blob must stay consistent (`POSTGRES_PASSWORD`, `APP_PASSWORD`, `FLYWAY_PASSWORD`, users).

1. Rotate passwords in your break-glass store; update AWS SM (seed script or `put-secret-value` with full JSON).
2. Force ESO sync for postgres + service ExternalSecrets.
3. Restart database StatefulSet, then dependent services:

   ```bash
   kubectl rollout restart statefulset/auth-db -n "$NAMESPACE"    # or user-db
   kubectl rollout status statefulset/auth-db -n "$NAMESPACE" --timeout=600s
   kubectl rollout restart deployment/auth-service -n "$NAMESPACE"
   ```

Repeat per database instance.

### RabbitMQ (`hermes/<env>/shared/rabbitmq`)

**Maps to:** `rabbitmq-secrets` + app secrets referencing broker creds.

1. Update AWS SM username/password JSON.
2. Force ESO sync.
3. Restart RabbitMQ, then auth-service, user-service, http-gateway (init container uses management API).

### GitHub OAuth / Keycloak admin / Cognito

| AWS path | Consumers |
|----------|-----------|
| `shared/github-oauth` | Keycloak IdP, auth-service |
| `services/keycloak/keycloak-admin` | Keycloak, auth-service |
| `services/auth-service/cognito` | auth-service (staging/prod) |

1. Rotate at the source (GitHub, Keycloak console, Cognito) **or** update AWS SM if Hermes is source of truth.
2. Force ESO sync.
3. Restart Keycloak and/or auth-service as needed.

## Verification

After any rotation:

```bash
# ESO status
kubectl get externalsecret -n "$NAMESPACE"

# Workload health (staging CI runs full smoke suite)
HERMES_ENV="$HERMES_ENV" bash infrastructure/k8s/scripts/smoke-test-secrets.sh
```

Review CloudTrail `GetSecretValue` / `PutSecretValue` events (see main [README](README.md#cloudtrail-and-alerts)).

## Future: automatic rotation

Phase D implements a **generic JSON rotation Lambda** (disabled by default). See [PHASE-D.md](../../secrets/PHASE-D.md) and Terraform `enable_automatic_rotation`.

```bash
cd infrastructure/terraform/secrets-manager
# enable_automatic_rotation = true in terraform.tfvars
./secrets-manager-tf.sh apply
```

**Still requires operator / ESO follow-up:** Postgres multi-user bundles, RabbitMQ broker password sync, Keycloak admin API updates, Cognito console/API for client secrets, and coordinated JWT restarts.

## Related docs

- [README](README.md) — Terraform, IRSA, seed script
- [DR.md](../../secrets/DR.md) — disaster recovery
- [SECURITY.md](../../secrets/SECURITY.md) — IAM boundaries and audit
