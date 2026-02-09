terraform {
  required_version = ">= 1.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    archive = {
      source  = "hashicorp/archive"
      version = "~> 2.0"
    }
  }

  # Uncomment and configure for remote state
  # backend "s3" {
  #   bucket         = "hermes-terraform-state"
  #   key            = "api-gateway/terraform.tfstate"
  #   region         = "us-east-1"
  #   encrypt        = true
  #   dynamodb_table = "hermes-terraform-locks"
  # }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = merge(var.tags, {
      Project     = var.project_name
      Environment = var.environment
      ManagedBy   = "terraform"
    })
  }
}

locals {
  name_prefix = "${var.project_name}-${var.environment}"
}

# Lambda Authorizer Module
module "lambda_authorizer" {
  source = "./modules/lambda-authorizer"

  name_prefix    = local.name_prefix
  jwks_url       = var.jwks_url
  jwt_issuer     = var.jwt_issuer
  cache_ttl_ms   = 900000  # 15 minutes
  gateway_secret = var.gateway_secret

  tags = var.tags
}

# API Gateway Module
module "api_gateway" {
  source = "./modules/api-gateway"

  name_prefix               = local.name_prefix
  environment               = var.environment
  user_service_url          = var.user_service_url
  lambda_authorizer_arn     = module.lambda_authorizer.function_arn
  lambda_authorizer_invoke_arn = module.lambda_authorizer.invoke_arn

  # VPC Link configuration for private ECS service
  vpc_id                = var.vpc_id
  private_subnet_ids    = var.private_subnet_ids
  ecs_security_group_id = var.ecs_security_group_id

  # Rate limiting
  rate_limit_authenticated   = var.rate_limit_authenticated
  rate_limit_unauthenticated = var.rate_limit_unauthenticated
  rate_limit_burst           = var.rate_limit_burst

  # Optional custom domain
  domain_name     = var.domain_name
  certificate_arn = var.certificate_arn

  tags = var.tags
}

# Grant API Gateway permission to invoke Lambda authorizer
resource "aws_lambda_permission" "api_gateway" {
  statement_id  = "AllowAPIGatewayInvoke"
  action        = "lambda:InvokeFunction"
  function_name = module.lambda_authorizer.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${module.api_gateway.api_execution_arn}/*"
}
