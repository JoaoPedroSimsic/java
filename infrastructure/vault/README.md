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

## Seed secrets

After bootstrap, populate Vault KV from the repo-root `.env` (same source as `setup-env.sh`):

```bash
make vault-seed
# or: ./infrastructure/vault/scripts/seed-dev-secrets.sh
```

The script writes idempotent `vault kv put` entries under `secret/hermes/dev/...`:

| Vault path | Keys |
|------------|------|
| `shared/jwt-signing-key` | `value` → `GATEWAY_SECRET` |
| `shared/rabbitmq` | `username`, `password` |
| `shared/github-oauth` | `client_id`, `client_secret` |
| `services/auth-db/postgres` | `POSTGRES_*`, `APP_*`, `FLYWAY_*` |
| `services/user-db/postgres` | same shape |
| `services/keycloak/keycloak-admin` | `username`, `password` |

Override environment with `HERMES_ENV=dev` (default) or point at `.env.dev` when present.

## External Secrets Operator (ESO)

- Helm chart + **ClusterSecretStore** + **ExternalSecret** manifests: `infrastructure/k8s/shared/external-secrets/overlays/dev` (single kustomize path in Skaffold).
- CRDs are **pre-installed** before each deploy via `infrastructure/k8s/shared/external-secrets/scripts/ensure-crds.sh` (Skaffold `deploy.kubectl.hooks.before`) using the pinned bundle at `infrastructure/k8s/shared/external-secrets/crds/bundle.yaml` (chart version **0.10.5**).
- Legacy paths `config/dev` and `manifests/dev` remain as source files referenced by the overlay; do not add them as separate Skaffold paths.

**Vault Agent Injector / CSI** are **not deployed** in dev (PRD §2 complete via ESO only). Chart values set `injector.enabled: false` to keep minikube footprint small. Revisit in Phase D if file-based injection is preferred over synced Kubernetes `Secret` objects.

### Verify ESO sync

```bash
kubectl get externalsecret -n hermes-dev
kubectl get secret gateway-secrets -n hermes-dev
# ExternalSecret status should show Ready / SecretSynced
```

**ExternalSecret manifests (Phase B):** ESO owns the canonical Kubernetes `Secret` names (`gateway-secrets`, `auth-service-secrets`, …) in `hermes-dev`. Dev overlays no longer run Kustomize `secretGenerator` for those secrets.

## `secrets.env` + `secretGenerator` (dev)

- **Phase B (default):** Dev overlays use **Vault + ESO only**. `setup-env.sh dev` writes **`params.env`** (ConfigMaps) and validates secret values in `.env` for `make vault-seed`; it does **not** write `secrets.env`.
- **Offline fallback:** `setup-env.sh dev --local-secrets` + `make back-local` (Skaffold profile `local-secrets` → `clusters/dev-local-secrets`) restores Kustomize `secretGenerator` from `secrets.env`. **Non-prod only** — unsafe for shared clusters.
- **`secrets.env.example`** remains the committed shape reference; never commit real `secrets.env` files.

## Offline / flaky network

- **Kubernetes path (default):** Requires Vault reachable for first boot (`make back` runs `vault-init`). If Vault is down after seed, ESO refresh keeps existing synced secrets until the pod restarts wipe Vault data.
- **Pure local fallback:** `setup-env.sh dev --local-secrets` then `make back-local` — skips Vault/ESO entirely.

## Makefile and Skaffold

- `make back` starts minikube, runs **`make vault-init`** (deploy Vault, bootstrap K8s auth, seed from `.env`), then `skaffold dev` for ESO + the app stack.
- Vault is **not** in Skaffold’s status check (StatefulSet rollout on minikube was causing false `1/16 failed` errors).
- Helpers:
  - `make vault-up` — deploy Vault and wait for `vault-0`
  - `make vault-init` — `vault-up` + bootstrap + seed (when `.env` exists)
  - `make vault-bootstrap` — runs `bootstrap-dev-k8s-auth.sh` with your current kubectl context.
  - `make vault-seed` — writes `.env` values into Vault KV (run after bootstrap).
  - `make vault-ui` — prints the suggested `kubectl port-forward` for the UI.
  - `make back-local` — Skaffold with `local-secrets` profile (no Vault)
  - `make teardown` — delete ESO CRs, `skaffold delete`, and the `vault` namespace

### Recommended dev workflow

```bash
cp .env.example .env          # if needed
infrastructure/k8s/setup-env.sh dev
make back                     # vault-init + skaffold dev (ESO syncs secrets)
kubectl get externalsecret -n hermes-dev
```

### Offline workflow

```bash
infrastructure/k8s/setup-env.sh dev --local-secrets
make back-local
```

## Runbooks

### Unseal

- **Dev overlay (`server.dev`):** not required (Vault is not sealed).
- **If you switch to a production-style server with seal:** follow HashiCorp unseal procedures for your backend (Shamir, cloud KMS, etc.) — not covered by the default dev values.

### Root token handling

- Treat dev root tokens as **public within the cluster**; rotate by restarting Vault in dev or re-boostrapping policies/roles.
- **Never** push root tokens to CI logs in clear text for real environments.

### Break-glass / local override

- Use `setup-env.sh dev --local-secrets` + `make back-local` when Vault/ESO must be bypassed for offline work; document in PRs when doing so on shared dev clusters.
