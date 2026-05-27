# Disaster recovery — secrets and cluster reconnect (PRD §5)

How to recreate Hermes secrets and reconnect workloads after **account loss**, **region failover**, or **EKS cluster rebuild**.

## Scope

| Environment | Secret store | Recovery path |
|-------------|--------------|---------------|
| **staging / prod** | AWS Secrets Manager + ESO | This document (§ AWS path) |
| **dev (minikube)** | Vault + ESO | [§ Dev Vault path](#dev-vault-path-minikube) |

Break-glass credential values must live **outside git** (password manager, encrypted backup, CI Environment secrets).

---

## AWS path (staging / prod)

### 1. Terraform — infrastructure

In the target AWS account/region:

```bash
cd infrastructure/terraform/secrets-manager
cp terraform.tfvars.example terraform.tfvars
# Set environment, eks_cluster_name, region, tags

./secrets-manager-tf.sh apply
```

Capture outputs:

```bash
export ESO_IRSA_ROLE_ARN="$(./secrets-manager-tf.sh output -raw eso_irsa_role_arn)"
export USER_SERVICE_IRSA_ROLE_ARN="$(./secrets-manager-tf.sh output -raw user_service_irsa_role_arn)"
export CLOUDTRAIL_NAME="$(./secrets-manager-tf.sh output -raw cloudtrail_name)"
```

Verify CloudTrail trail and Secrets Manager alarm are active (see [terraform README](../terraform/secrets-manager/README.md#cloudtrail-and-alerts)).

### 2. Restore secret values

From break-glass `.env.<env>` or org backup:

```bash
export HERMES_ENV=prod   # or staging
bash infrastructure/scripts/seed-secrets.sh
```

Confirm secrets exist:

```bash
aws secretsmanager list-secrets \
  --filters Key=name,Values="hermes/${HERMES_ENV}/" \
  --region "${AWS_REGION:-sa-east-1}"
```

### 3. EKS and IRSA

1. Provision or restore EKS with **OIDC provider** enabled.
2. Re-run Terraform if the cluster name changed (IRSA trust policies bind to OIDC issuer URL).
3. Configure kubectl:

   ```bash
   aws eks update-kubeconfig --name "$EKS_CLUSTER_NAME" --region "$AWS_REGION"
   ```

4. Deploy ESO and sync secrets:

   ```bash
   export ESO_IRSA_ROLE_ARN
   HERMES_ENV="$HERMES_ENV" make eso-sync-staging   # or eso-sync-prod
   ```

### 4. Application manifests

Apply the cluster overlay (substitute user-service IRSA ARN):

```bash
export USER_SERVICE_IRSA_ROLE_ARN
kubectl kustomize "infrastructure/k8s/clusters/${HERMES_ENV}" \
  --load-restrictor=LoadRestrictionsNone --enable-helm \
  | sed "s|PLACEHOLDER_USER_SERVICE_IRSA_ROLE_ARN|${USER_SERVICE_IRSA_ROLE_ARN}|g" \
  | kubectl apply --server-side --force-conflicts -f -
```

Or use GitHub Actions: `.github/workflows/deploy-kubernetes.yml` with `workflow_dispatch` and the target environment.

### 5. Verification

```bash
HERMES_ENV="$HERMES_ENV" bash infrastructure/k8s/scripts/smoke-test-secrets.sh
```

Check CloudTrail for unexpected `GetSecretValue` after cutover.

### 6. Post-incident

- Rotate all credentials if compromise is suspected (see [ROTATION.md](../terraform/secrets-manager/ROTATION.md)).
- Update GitHub Environment `vars` (`ESO_IRSA_ROLE_ARN`, `USER_SERVICE_IRSA_ROLE_ARN`, `EKS_CLUSTER_NAME`).
- Document RTO/RPO achieved for your org.

---

## Dev Vault path (minikube)

Dev Vault uses **in-memory storage** — pod restart wipes KV data. Recovery is reprovision, not restore.

```bash
make vault-reset          # or: kubectl delete namespace vault
make vault-init           # bootstrap K8s auth + seed from .env
make eso-sync
make back                 # or skaffold dev
```

If `.env` is lost, recreate from `.env.example` + `infrastructure/k8s/setup-env.sh dev` (non-prod values only).

For persistent dev Vault (optional future profile), adopt Raft snapshots — see [vault README](../vault/README.md#operations-backup-and-recovery).

---

## Related docs

- [ROTATION.md](../terraform/secrets-manager/ROTATION.md)
- [INTEGRATION.md](../INTEGRATION.md)
- [SECURITY.md](SECURITY.md)
