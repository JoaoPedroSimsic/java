# HTTP API Gateway (v2) - More cost-effective than REST API
resource "aws_apigatewayv2_api" "main" {
  name          = "${var.name_prefix}-api"
  protocol_type = "HTTP"
  description   = "API Gateway for Hermes platform"

  cors_configuration {
    allow_credentials = true
    allow_headers     = ["*"]
    allow_methods     = ["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"]
    allow_origins     = ["*"]  # Restrict in production
    expose_headers    = ["*"]
    max_age           = 3600
  }

  tags = var.tags
}

# Lambda Authorizer
resource "aws_apigatewayv2_authorizer" "jwt" {
  api_id                            = aws_apigatewayv2_api.main.id
  authorizer_type                   = "REQUEST"
  authorizer_uri                    = var.lambda_authorizer_invoke_arn
  identity_sources                  = ["$request.header.Cookie", "$request.header.Authorization"]
  name                              = "${var.name_prefix}-jwt-authorizer"
  authorizer_payload_format_version = "2.0"
  authorizer_result_ttl_in_seconds  = 300  # Cache authorizer results for 5 minutes
  enable_simple_responses           = true
}

# VPC Link for private ECS service
resource "aws_apigatewayv2_vpc_link" "main" {
  name               = "${var.name_prefix}-vpc-link"
  security_group_ids = [aws_security_group.vpc_link.id]
  subnet_ids         = var.private_subnet_ids

  tags = var.tags
}

# Security group for VPC Link
resource "aws_security_group" "vpc_link" {
  name        = "${var.name_prefix}-vpc-link-sg"
  description = "Security group for API Gateway VPC Link"
  vpc_id      = var.vpc_id

  egress {
    from_port       = 8081
    to_port         = 8081
    protocol        = "tcp"
    security_groups = [var.ecs_security_group_id]
  }

  tags = var.tags
}

# Integration with user service via VPC Link
resource "aws_apigatewayv2_integration" "user_service" {
  api_id                 = aws_apigatewayv2_api.main.id
  integration_type       = "HTTP_PROXY"
  integration_uri        = var.user_service_url
  integration_method     = "ANY"
  connection_type        = "VPC_LINK"
  connection_id          = aws_apigatewayv2_vpc_link.main.id
  payload_format_version = "1.0"

  request_parameters = {
    "overwrite:header.X-User-Email"    = "$context.authorizer.userEmail"
    "overwrite:header.X-User-Id"       = "$context.authorizer.userId"
    "overwrite:header.X-Gateway-Secret" = "$context.authorizer.gatewaySecret"
  }
}

# Public routes (no auth required)
resource "aws_apigatewayv2_route" "user_sync" {
  api_id    = aws_apigatewayv2_api.main.id
  route_key = "POST /api/users/sync"
  target    = "integrations/${aws_apigatewayv2_integration.user_service.id}"
}

# Protected routes (auth required)
resource "aws_apigatewayv2_route" "users_get" {
  api_id             = aws_apigatewayv2_api.main.id
  route_key          = "GET /api/users"
  target             = "integrations/${aws_apigatewayv2_integration.user_service.id}"
  authorization_type = "CUSTOM"
  authorizer_id      = aws_apigatewayv2_authorizer.jwt.id
}

resource "aws_apigatewayv2_route" "user_get" {
  api_id             = aws_apigatewayv2_api.main.id
  route_key          = "GET /api/users/{id}"
  target             = "integrations/${aws_apigatewayv2_integration.user_service.id}"
  authorization_type = "CUSTOM"
  authorizer_id      = aws_apigatewayv2_authorizer.jwt.id
}

resource "aws_apigatewayv2_route" "user_patch" {
  api_id             = aws_apigatewayv2_api.main.id
  route_key          = "PATCH /api/users/{id}"
  target             = "integrations/${aws_apigatewayv2_integration.user_service.id}"
  authorization_type = "CUSTOM"
  authorizer_id      = aws_apigatewayv2_authorizer.jwt.id
}

resource "aws_apigatewayv2_route" "user_delete" {
  api_id             = aws_apigatewayv2_api.main.id
  route_key          = "DELETE /api/users/{id}"
  target             = "integrations/${aws_apigatewayv2_integration.user_service.id}"
  authorization_type = "CUSTOM"
  authorizer_id      = aws_apigatewayv2_authorizer.jwt.id
}

# Stage
resource "aws_apigatewayv2_stage" "main" {
  api_id      = aws_apigatewayv2_api.main.id
  name        = var.environment
  auto_deploy = true

  default_route_settings {
    throttling_burst_limit = var.rate_limit_burst
    throttling_rate_limit  = var.rate_limit_authenticated
  }

  access_log_settings {
    destination_arn = aws_cloudwatch_log_group.api_gateway.arn
    format = jsonencode({
      requestId         = "$context.requestId"
      ip                = "$context.identity.sourceIp"
      requestTime       = "$context.requestTime"
      httpMethod        = "$context.httpMethod"
      routeKey          = "$context.routeKey"
      status            = "$context.status"
      protocol          = "$context.protocol"
      responseLength    = "$context.responseLength"
      integrationError  = "$context.integrationErrorMessage"
      authorizerError   = "$context.authorizer.error"
      userEmail         = "$context.authorizer.userEmail"
    })
  }

  tags = var.tags
}

# CloudWatch Log Group for access logs
resource "aws_cloudwatch_log_group" "api_gateway" {
  name              = "/aws/apigateway/${var.name_prefix}-api"
  retention_in_days = 14

  tags = var.tags
}

# Custom domain (optional)
resource "aws_apigatewayv2_domain_name" "main" {
  count = var.domain_name != "" ? 1 : 0

  domain_name = var.domain_name

  domain_name_configuration {
    certificate_arn = var.certificate_arn
    endpoint_type   = "REGIONAL"
    security_policy = "TLS_1_2"
  }

  tags = var.tags
}

resource "aws_apigatewayv2_api_mapping" "main" {
  count = var.domain_name != "" ? 1 : 0

  api_id      = aws_apigatewayv2_api.main.id
  domain_name = aws_apigatewayv2_domain_name.main[0].id
  stage       = aws_apigatewayv2_stage.main.id
}
