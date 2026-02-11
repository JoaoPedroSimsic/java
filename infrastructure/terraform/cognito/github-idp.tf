resource "aws_cognito_identity_provider" "github" {
  user_pool_id  = aws_cognito_user_pool.main.id
  provider_name = "GitHub"
  provider_type = "OIDC"

  provider_details = {
    authorize_scopes          = "user:email read:user"
    client_id                 = var.github_client_id
    client_secret             = var.github_client_secret
    oidc_issuer               = "https://github.com"
    authorize_url             = "https://github.com/login/oauth/authorize"
    token_url                 = "https://github.com/login/oauth/access_token"
    attributes_url            = "https://api.github.com/user"
    attributes_url_add_attributes = "false"
    
    attributes_request_method = "GET"
  }

  attribute_mapping = {
    email    = "email"
    name     = "name"
    username = "login"
    "custom:github_id" = "id"
  }

  lifecycle {
    prevent_destroy = false 
  }
}
