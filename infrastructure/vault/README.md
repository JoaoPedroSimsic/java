# HashiCorp Vault — development (minikube)

This folder supports **PRD §2 — Development — HashiCorp Vault**: Vault runs **in-cluster on minikube** alongside the Hermes stack deployed by Skaffold (`make back`). The supported path matches `Makefile` + Skaffold.

## Deployment model

| Topic | Choice |
|--------|--------|
| Where | **Kubernetes** (`vault` namespace), installed via **official HashiCorp Helm chart** (`infrastructure/k8s/shared/vault/overlays/dev`). |
| Unseal | **Not applicable** for the default overlay: `server.dev.enabled: true` runs a **development** Vault (single process, in-memory storage, auto-unsealed). |
| Persistence | **None** in the default dev chart values: restarting the pod **wipes** data. Use this only for local/shared-dev experimentation. For persistent dev Vault, switch to a standalone/raft chart profile (documented as a follow-up; not committed here). |
| Network | Vault API and UI: **8200** inside the cluster (`vault.vault.svc.cluster.local:8200`). |

### Ports and UI

- From the workstation: `kubectl port-forward -n vault svc/vault 8200:8200` then open `http://127.0.0.1:8200/ui` (dev server prints a **root token** in pod logs — treat it as **compromised-by-design** for local use only).
- **Do not** commit root tokens, bootstrap files, or `.env` secrets. Add local-only material under ignored paths (see repository `.gitignore`).

## KV secrets engine

- Bootstrap enables **KV v2** at mount path `secret/` (PRD §1.2 paths become `secret/data/hermes/...`).
- **Database / RabbitMQ** dynamic engines are **out of scope** for phase 1; add later if you adopt dynamic credentials.

## Keycloak (dev) — Vault Keycloak secrets engine

| Option | Pros | Cons |
|--------|------|------|
| **KV v2** storing `KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD` (today’s shape) | Simple; works with ESO and existing env mapping; easy rotation procedure. | Long-lived static passwords unless rotated out-of-band. |
| **Vault Keycloak secrets engine** (dynamic short-lived admin credentials) | Reduces exposure window for admin API access; aligns with “prefer short-lived” posture. | Extra engine to operate; must align Keycloak version/plugin expectations; Hermes `KeycloakAdapter` flows need explicit design so clients pick up rotated creds. |

**Recommendation for phase 1:** stay on **KV v2** for Keycloak material; track **Keycloak secrets engine** as a later hardening step once auth-service bootstrap and ESO mappings are stable.

## Policies and authentication

- **HCL policies** live in `infrastructure/vault/policies/`:
  - `hermes-apps-read.hcl` — read/list under `secret/data/hermes/*` and metadata (used by ESO Kubernetes auth role).
  - `hermes-admin.hcl` — **reference only** for human operators (broad); **not** applied automatically by scripts. Prefer root token only for bootstrap on dev.
- **Kubernetes auth** is the preferred in-cluster method: Vault validates projected ServiceAccount JWTs; ESO uses its controller ServiceAccount.
- **Token / AppRole** remain valid for **local scripts** and CI if you add them later; do not store long-lived tokens in git.

## Bootstrap (Kubernetes auth + ESO)

After Skaffold has created the Vault pod:

1. Ensure the `vault` ServiceAccount can call **TokenReview** (the Vault Helm chart installs `vault-server-binding` → `system:auth-delegator`).
2. Run:

   ```bash
   chmod +x infrastructure/vault/scripts/bootstrap-dev-k8s-auth.sh
   MINIKUBE_PROFILE=hermes-dev kubectl config use-context minikube
   ./infrastructure/vault/scripts/bootstrap-dev-k8s-auth.sh
   ```

3. If your External Secrets release uses a non-default ServiceAccount name, override before running:

   ```bash
   ESO_SERVICE_ACCOUNT=external-secrets ESO_NAMESPACE=external-secrets \
     ./infrastructure/vault/scripts/bootstrap-dev-k8s-auth.sh
   ```

The script enables `kubernetes` auth, configures the Kubernetes API connection from **inside** the Vault pod, writes the `hermes-apps-read` policy, and creates the `external-secrets` Vault role expected by `ClusterSecretStore` `vault-hermes-dev`.

**Root token:** export `VAULT_ROOT_TOKEN` or let the script parse `Root Token:` from `kubectl logs -n vault vault-0`. Optionally save a token locally with a **gitignored** filename (see `.gitignore`); never commit it.

## External Secrets Operator (ESO)

- Helm chart: `infrastructure/k8s/shared/external-secrets/overlays/dev` (installs CRDs + controller + webhook).
- **ClusterSecretStore** `vault-hermes-dev`: `infrastructure/k8s/shared/external-secrets/config/dev` (applied **after** the chart so CRDs exist).
- Example consumer manifest (not wired into deployments yet — **Phase A** side-by-side with `secretGenerator`): `infrastructure/vault/examples/external-secret-jwt.yaml`.

**Vault Agent Injector** is disabled in chart values (`injector.enabled: false`) to keep minikube footprint small; enable it in a follow-up if you prefer file-based injection over ESO.

## `secrets.env` + `secretGenerator` (dev)

- **Phase A (current):** Kustomize **`secretGenerator` + `setup-env.sh` / `secrets.env`** remain the default for application `Secret` objects. Vault + ESO are **additive** so `make back` keeps working without mandatory Vault bootstrap.
- **Later:** replace or gate dev overlays so Deployments reference ESO-managed `Secret` names only; keep **`secrets.env.example`** as the shape reference (no real values).

## Offline / flaky network

- **Kubernetes path:** If Vault is down, skip bootstrap; apps still start from `secrets.env` generated by `infrastructure/k8s/setup-env.sh` from `.env`.
- **Non-K8s / pure local:** Copy `secrets.env.example` → `secrets.env` per overlay (documented in overlay READMEs / setup-env); label these flows **non-prod** and unsafe for shared clusters.

## Makefile and Skaffold

- `make back` still starts minikube (if needed) and runs `skaffold dev`. Vault and ESO are deployed as **additional** kustomize paths (see root `skaffold.yaml`).
- Helpers:
  - `make vault-bootstrap` — runs `bootstrap-dev-k8s-auth.sh` with your current kubectl context.
  - `make vault-ui` — prints the suggested `kubectl port-forward` for the UI.

## Runbooks

### Unseal

- **Dev overlay (`server.dev`):** not required (Vault is not sealed).
- **If you switch to a production-style server with seal:** follow HashiCorp unseal procedures for your backend (Shamir, cloud KMS, etc.) — not covered by the default dev values.

### Root token handling

- Treat dev root tokens as **public within the cluster**; rotate by restarting Vault in dev or re-boostrapping policies/roles.
- **Never** push root tokens to CI logs in clear text for real environments.

### Break-glass / local override

- Use `.env` + `setup-env.sh` and existing `secretGenerator` paths when Vault/ESO must be bypassed for offline work; document in PRs when doing so on shared dev clusters.
