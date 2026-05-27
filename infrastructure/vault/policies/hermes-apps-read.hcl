path "secret/data/hermes/*" {
  capabilities = ["read"]
}

path "secret/metadata/hermes/*" {
  capabilities = ["list", "read"]
}

path "database/static-creds/*" {
  capabilities = ["read"]
}

path "database/creds/*" {
  capabilities = ["read"]
}
