output "user_pool_id" {
  description = "Cognito User Pool ID"
  value       = aws_cognito_user_pool.main.id
}

output "user_pool_arn" {
  description = "Cognito User Pool ARN"
  value       = aws_cognito_user_pool.main.arn
}

output "client_id" {
  description = "Cognito User Pool Client ID (for frontend)"
  value       = aws_cognito_user_pool_client.main.id
}

output "jwks_url" {
  description = "JWKS URL for JWT validation in the gateway"
  value       = "https://cognito-idp.${var.aws_region}.amazonaws.com/${aws_cognito_user_pool.main.id}/.well-known/jwks.json"
}

output "issuer" {
  description = "JWT Issuer URL for token validation"
  value       = "https://cognito-idp.${var.aws_region}.amazonaws.com/${aws_cognito_user_pool.main.id}"
}

output "cognito_domain" {
  description = "Cognito hosted domain for OAuth2"
  value       = "${aws_cognito_user_pool_domain.main.domain}.auth.${var.aws_region}.amazoncognito.com"
}

output "oauth2_authorize_url" {
  description = "OAuth2 authorization endpoint"
  value       = "https://${aws_cognito_user_pool_domain.main.domain}.auth.${var.aws_region}.amazoncognito.com/oauth2/authorize"
}

output "oauth2_token_url" {
  description = "OAuth2 token endpoint"
  value       = "https://${aws_cognito_user_pool_domain.main.domain}.auth.${var.aws_region}.amazoncognito.com/oauth2/token"
}

output "oauth2_userinfo_url" {
  description = "OAuth2 userinfo endpoint"
  value       = "https://${aws_cognito_user_pool_domain.main.domain}.auth.${var.aws_region}.amazoncognito.com/oauth2/userInfo"
}

output "oauth2_logout_url" {
  description = "OAuth2 logout endpoint"
  value       = "https://${aws_cognito_user_pool_domain.main.domain}.auth.${var.aws_region}.amazoncognito.com/logout"
}

output "github_login_url" {
  description = "URL to initiate GitHub OAuth login"
  value       = "${aws_apigatewayv2_api.github_auth.api_endpoint}/auth/github"
}

output "github_auth_api_endpoint" {
  description = "API Gateway endpoint for GitHub auth"
  value       = aws_apigatewayv2_api.github_auth.api_endpoint
}

output "github_oauth_callback_url" {
  description = "Set this URL as the 'Authorization callback URL' in your GitHub OAuth App settings"
  value       = "${aws_apigatewayv2_api.github_auth.api_endpoint}/auth/github/callback"
}

output "env_variables" {
  description = "Environment variables for application configuration"
  value = {
    COGNITO_USER_POOL_ID = aws_cognito_user_pool.main.id
    COGNITO_CLIENT_ID    = aws_cognito_user_pool_client.main.id
    COGNITO_JWKS_URL     = "https://cognito-idp.${var.aws_region}.amazonaws.com/${aws_cognito_user_pool.main.id}/.well-known/jwks.json"
    COGNITO_ISSUER       = "https://cognito-idp.${var.aws_region}.amazonaws.com/${aws_cognito_user_pool.main.id}"
    COGNITO_DOMAIN       = "${aws_cognito_user_pool_domain.main.domain}.auth.${var.aws_region}.amazoncognito.com"
    GITHUB_AUTH_URL      = "${aws_apigatewayv2_api.github_auth.api_endpoint}/auth/github"
  }
}
