path "secret/data/hermes/*" {
  capabilities = ["read"]
}

path "secret/metadata/hermes/*" {
  capabilities = ["list", "read"]
}

# Phase D — Vault database engine (static / dynamic creds for dev opt-in).
path "database/static-creds/*" {
  capabilities = ["read"]
}

path "database/creds/*" {
  capabilities = ["read"]
}
