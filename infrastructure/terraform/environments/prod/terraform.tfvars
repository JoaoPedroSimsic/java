# AWS Configuration
aws_region  = "us-east-1"
environment = "prod"
project_name = "hermes"

# User Service Configuration
# Replace with your actual ECS service endpoint
user_service_url = "http://internal-alb.example.com:8081"

# Auth0 Configuration (Production)
# Replace with your Auth0 tenant details
jwks_url   = "https://YOUR_DOMAIN.auth0.com/.well-known/jwks.json"
jwt_issuer = "https://YOUR_DOMAIN.auth0.com/"

# Gateway Secret (shared between API Gateway Lambda and backend services)
# IMPORTANT: Use a strong, unique secret. Consider using AWS Secrets Manager.
# Set via environment variable: TF_VAR_gateway_secret=your-secret
gateway_secret = "CHANGE_ME_USE_TF_VAR_OR_SECRETS_MANAGER"

# VPC Configuration
# Replace with your actual VPC and subnet IDs
vpc_id               = "vpc-xxxxxxxxxxxxxxxxx"
private_subnet_ids   = ["subnet-xxxxxxxxxxxxxxxxx", "subnet-yyyyyyyyyyyyyyyyy"]
ecs_security_group_id = "sg-xxxxxxxxxxxxxxxxx"

# Rate Limiting
rate_limit_authenticated   = 100  # 100 req/sec for authenticated users
rate_limit_unauthenticated = 5    # 5 req/sec for unauthenticated users
rate_limit_burst           = 200

# Custom Domain (optional - uncomment if using)
# domain_name     = "api.hermes.example.com"
# certificate_arn = "arn:aws:acm:us-east-1:123456789012:certificate/xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"

# Tags
tags = {
  Project     = "hermes"
  Environment = "prod"
  Team        = "platform"
}
