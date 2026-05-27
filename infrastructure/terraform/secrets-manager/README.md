# AWS Secrets Manager — production and staging (PRD §3)

Terraform provisions **AWS Secrets Manager** secrets, **IRSA** roles for External Secrets Operator and **user-service**, and **CloudTrail** auditing for Secrets Manager API calls.

## Prerequisites

- AWS CLI and Terraform >= 1.0
- An **EKS** cluster with OIDC provider enabled
- IAM permissions to create Secrets Manager secrets, IAM roles, CloudTrail, S3, and CloudWatch

## Quick start

```bash
cd infrastructure/terraform/secrets-manager
cp terraform.tfvars.example terraform.tfvars
# Edit terraform.tfvars (region, eks_cluster_name, S3 bucket, environment)

./secrets-manager-tf.sh plan
./secrets-manager-tf.sh apply
```

Capture outputs for Kubernetes deployment:

```bash
./secrets-manager-tf.sh output -json
```

## Secret naming (PRD §1.2)

| Kind | AWS name pattern | Example (prod) |
|------|------------------|----------------|
| Shared | `hermes/<env>/shared/<name>` | `hermes/prod/shared/jwt-signing-key` |
| Service-specific | `hermes/<env>/services/<service>/<name>` | `hermes/prod/services/auth-db/postgres` |

Terraform creates all secrets listed in `secrets.tf`. Populate values with the seed script (never commit real values):

```bash
HERMES_ENV=prod bash infrastructure/aws/scripts/seed-secrets.sh
```

## IRSA

| Workload | IAM role output | Kubernetes ServiceAccount |
|----------|-----------------|---------------------------|
| External Secrets Operator | `eso_irsa_role_arn` | `external-secrets/external-secrets` |
| user-service (S3) | `user_service_irsa_role_arn` | `hermes-<env>/user-service` |

ESO reads only secrets under `hermes/<env>/`. user-service uses **IRSA** instead of long-lived `AWS_ACCESS_KEY_ID` keys in Kubernetes Secrets.

Deploy ESO with the role ARN:

```bash
export ESO_IRSA_ROLE_ARN="$(./secrets-manager-tf.sh output -raw eso_irsa_role_arn)"
HERMES_ENV=prod bash infrastructure/k8s/shared/external-secrets/scripts/deploy-aws.sh
```

Annotate user-service after terraform apply (or patch `serviceaccount.yaml` before `kubectl apply -k clusters/prod`):

```bash
export USER_SERVICE_IRSA_ROLE_ARN="$(./secrets-manager-tf.sh output -raw user_service_irsa_role_arn)"
kubectl annotate serviceaccount user-service -n hermes-prod \
  eks.amazonaws.com/role-arn="$USER_SERVICE_IRSA_ROLE_ARN" --overwrite
```

## CloudTrail and alerts

- Trail name: output `cloudtrail_name`
- CloudWatch log group: `cloudtrail_log_group_name`
- Metric filter on `GetSecretValue` with alarm `secretsmanager_get_alarm_name`

Subscribe the alarm to SNS/PagerDuty in your org’s observability stack.

## CI/CD

Production Kubernetes deploys must **not** use `secrets.env` or Kustomize `secretGenerator`. Apply:

1. Terraform (secrets + IAM + CloudTrail)
2. `seed-secrets.sh` (from CI OIDC or break-glass operator session)
3. `deploy-aws.sh` + `wait-for-synced-secrets.sh`
4. `kubectl apply -k infrastructure/k8s/clusters/prod`

See `.github/workflows/deploy-k8s-prod.yml` for a GitHub Actions OIDC skeleton.

## Rotation and DR

- Manual rotation procedures: [ROTATION.md](ROTATION.md)
- Automatic rotation (Phase D): [PHASE-D.md](../../secrets/PHASE-D.md) — set `enable_automatic_rotation = true`
- Disaster recovery (new account/region): [DR.md](../../secrets/DR.md)

## Staging

Use the same module with `environment = "staging"` and a separate `terraform.tfvars` (or workspace). ESO manifests live under `manifests/staging/` with ClusterSecretStore `aws-hermes-staging`.
