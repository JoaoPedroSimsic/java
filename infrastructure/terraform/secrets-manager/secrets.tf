locals {
  shared_secrets = {
    jwt_signing_key = {
      name        = "${local.secret_prefix}/shared/jwt-signing-key"
      description = "Shared JWT HMAC signing key (GATEWAY_SECRET / app.jwt.secret)"
      json_keys   = ["value"]
    }
    rabbitmq = {
      name        = "${local.secret_prefix}/shared/rabbitmq"
      description = "Shared RabbitMQ broker credentials"
      json_keys   = ["username", "password"]
    }
    github_oauth = {
      name        = "${local.secret_prefix}/shared/github-oauth"
      description = "GitHub OAuth client credentials for Keycloak IdP"
      json_keys   = ["client_id", "client_secret"]
    }
  }

  service_secrets = {
    auth_db_postgres = {
      name        = "${local.secret_prefix}/services/auth-db/postgres"
      description = "Auth Postgres user passwords"
      json_keys = [
        "POSTGRES_USER", "POSTGRES_PASSWORD", "POSTGRES_DB",
        "APP_USER", "APP_PASSWORD", "FLYWAY_USER", "FLYWAY_PASSWORD",
      ]
    }
    user_db_postgres = {
      name        = "${local.secret_prefix}/services/user-db/postgres"
      description = "User Postgres user passwords"
      json_keys = [
        "POSTGRES_USER", "POSTGRES_PASSWORD", "POSTGRES_DB",
        "APP_USER", "APP_PASSWORD", "FLYWAY_USER", "FLYWAY_PASSWORD",
      ]
    }
    keycloak_admin = {
      name        = "${local.secret_prefix}/services/keycloak/keycloak-admin"
      description = "Keycloak admin console credentials"
      json_keys   = ["username", "password"]
    }
    auth_service_cognito = {
      name        = "${local.secret_prefix}/services/auth-service/cognito"
      description = "Auth-service Cognito client secret (prod/staging)"
      json_keys   = ["client_secret"]
    }
  }

  all_secrets = merge(local.shared_secrets, local.service_secrets)
}

resource "aws_secretsmanager_secret" "hermes" {
  for_each = local.all_secrets

  name                    = each.value.name
  description             = each.value.description
  recovery_window_in_days = var.environment == "prod" ? 30 : 7

  tags = merge(var.tags, {
    Environment = var.environment
    SecretKind  = startswith(each.value.name, "${local.secret_prefix}/shared/") ? "shared" : "service-specific"
  })
}

resource "aws_secretsmanager_secret_version" "hermes_placeholder" {
  for_each = var.seed_placeholder_secrets ? local.all_secrets : {}

  secret_id = aws_secretsmanager_secret.hermes[each.key].id
  secret_string = jsonencode({
    for key in each.value.json_keys : key => "REPLACE_ME"
  })

  lifecycle {
    ignore_changes = [secret_string]
  }
}
