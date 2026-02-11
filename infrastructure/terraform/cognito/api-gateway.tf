# API Gateway HTTP API
resource "aws_apigatewayv2_api" "github_auth" {
  name          = "${var.app_name}-github-auth-${var.environment}"
  protocol_type = "HTTP"

  cors_configuration {
    allow_origins = ["*"]
    allow_methods = ["GET", "OPTIONS"]
    allow_headers = ["Content-Type", "Authorization"]
    max_age       = 300
  }

  tags = var.tags
}

# API Gateway Stage
resource "aws_apigatewayv2_stage" "github_auth" {
  api_id      = aws_apigatewayv2_api.github_auth.id
  name        = "$default"
  auto_deploy = true

  access_log_settings {
    destination_arn = aws_cloudwatch_log_group.api_gateway.arn
    format = jsonencode({
      requestId      = "$context.requestId"
      ip             = "$context.identity.sourceIp"
      requestTime    = "$context.requestTime"
      httpMethod     = "$context.httpMethod"
      routeKey       = "$context.routeKey"
      status         = "$context.status"
      protocol       = "$context.protocol"
      responseLength = "$context.responseLength"
      errorMessage   = "$context.error.message"
    })
  }

  tags = var.tags
}

# CloudWatch Log Group for API Gateway
resource "aws_cloudwatch_log_group" "api_gateway" {
  name              = "/aws/apigateway/${var.app_name}-github-auth-${var.environment}"
  retention_in_days = 14

  tags = var.tags
}

# Lambda Integration
resource "aws_apigatewayv2_integration" "github_auth" {
  api_id                 = aws_apigatewayv2_api.github_auth.id
  integration_type       = "AWS_PROXY"
  integration_uri        = aws_lambda_function.github_auth.invoke_arn
  integration_method     = "POST"
  payload_format_version = "2.0"
}

# Route: GET /auth/github (initiate OAuth)
resource "aws_apigatewayv2_route" "github_auth_initiate" {
  api_id    = aws_apigatewayv2_api.github_auth.id
  route_key = "GET /auth/github"
  target    = "integrations/${aws_apigatewayv2_integration.github_auth.id}"
}

# Route: GET /auth/github/callback (OAuth callback)
resource "aws_apigatewayv2_route" "github_auth_callback" {
  api_id    = aws_apigatewayv2_api.github_auth.id
  route_key = "GET /auth/github/callback"
  target    = "integrations/${aws_apigatewayv2_integration.github_auth.id}"
}
