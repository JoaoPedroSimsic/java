# Security review — secret management (PRD §6)

Summary of security controls for Hermes secret management across environments.

## IAM boundaries (staging / prod)

Terraform in [`infrastructure/terraform/secrets-manager/iam.tf`](../terraform/secrets-manager/iam.tf) enforces least privilege:

| Identity | Can do | Cannot do |
|----------|--------|-----------|
| **ESO IRSA** (`external-secrets` SA) | `GetSecretValue`, `DescribeSecret` on `hermes/<env>/*` secrets; scoped `ListSecrets` | Write secrets; read other app secrets |
| **user-service IRSA** | S3 object access (app workload) | Secrets Manager read (separate from ESO) |

ESO controller identity is **separate** from application IRSA unless deliberately merged.

Review IAM changes with the same Terraform module; avoid hand-edited wildcard `secretsmanager:*` policies.

## Audit and alerting

- **CloudTrail** logs Secrets Manager API calls to S3 + CloudWatch ([`cloudtrail.tf`](../terraform/secrets-manager/cloudtrail.tf)).
- Metric filter on `GetSecretValue` drives alarm `secretsmanager_get_alarm_name`.
- Subscribe the alarm to SNS/PagerDuty; investigate spikes after rotation or DR events.

## Dev — Vault API

| Control | Status |
|---------|--------|
| TLS on Vault API | **Not enabled** — dev server uses HTTP on port 8200 inside the cluster |
| Network isolation | **NetworkPolicy** on `vault` namespace — ingress 8200 from ESO + same namespace only |
| Root token | Ephemeral; never commit; see [vault README](../vault/README.md) |
| Agent Injector | Disabled (`injector.enabled: false`) |

**Risk acceptance:** HTTP Vault is acceptable for local minikube only. Do not expose Vault outside the cluster. Operator `kubectl port-forward` bypasses NetworkPolicy by design (kubelet/host path).

## Staging / prod — no in-cluster Vault

ESO reads AWS Secrets Manager over **HTTPS**. No Vault pod in cluster. Restrict egress at the node/CNI level if your org requires explicit allowlists (optional; not committed in phase 1).

## Git and CI guardrails

- `.gitignore` blocks `.env`, `secrets.env`, `terraform.tfvars`, vault tokens.
- **pre-commit:** gitleaks + detect-secrets (baseline in `.secrets.baseline`).
- **CI:** `secret-scan` job in `.github/workflows/ci.yml`.

Install locally:

```bash
pip install pre-commit
pre-commit install
```

## Actuator and health endpoints

Spring Boot services disable `/actuator/env` and hide property values on exposed endpoints — see [`INTEGRATION.md`](../INTEGRATION.md).

Smoke tests call `/actuator/health` only and assert responses do not contain known secret key names.

## NetworkPolicy — Vault namespace

Manifest: [`infrastructure/k8s/shared/vault/overlays/dev/network-policy.yaml`](../k8s/shared/vault/overlays/dev/network-policy.yaml)

- Default deny ingress in `vault` namespace.
- Allow TCP 8200 from pods in `external-secrets` namespace (ESO → Vault).
- Allow intra-namespace traffic (Vault server components).

Does **not** restrict ESO egress to AWS (staging/prod) — out of scope for phase 1.

## Related docs

- [ROTATION.md](../terraform/secrets-manager/ROTATION.md)
- [DR.md](DR.md)
- [PHASE-D.md](PHASE-D.md)
- [INTEGRATION.md](../INTEGRATION.md)
