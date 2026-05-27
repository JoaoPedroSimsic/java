# AWS integration (Hermes)

Scripts and runbooks for **production/staging** secret delivery via AWS Secrets Manager (PRD §3).

| Task | Command |
|------|---------|
| Provision infrastructure | See [../terraform/secrets-manager/README.md](../terraform/secrets-manager/README.md) |
| Seed secret values | `HERMES_ENV=prod make aws-secrets-seed` |
| Deploy ESO + ExternalSecrets | `ESO_IRSA_ROLE_ARN=... HERMES_ENV=prod make eso-sync-prod` |

Never commit `.env.prod`, `.env.staging`, or real secret values. Use CI OIDC or operator sessions with `seed-secrets.sh`.
