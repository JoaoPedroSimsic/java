path "secret/data/hermes/*" {
  capabilities = ["read"]
}

path "secret/metadata/hermes/*" {
  capabilities = ["list", "read"]
}
