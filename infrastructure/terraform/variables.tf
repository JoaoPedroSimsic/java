variable "aws_region" {
  description = "AWS region for resources"
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Environment name (e.g., prod, staging)"
  type        = string
  default     = "prod"
}

variable "project_name" {
  description = "Project name for resource naming"
  type        = string
  default     = "hermes"
}

# ECS Service Configuration
variable "user_service_url" {
  description = "URL of the user service (ECS service endpoint)"
  type        = string
}

variable "jwks_url" {
  description = "URL of the JWKS endpoint on user service"
  type        = string
}

variable "jwt_issuer" {
  description = "Expected JWT issuer"
  type        = string
  default     = "hermes-user-service"
}

variable "gateway_secret" {
  description = "Secret shared between API Gateway and backend services for request validation"
  type        = string
  sensitive   = true
}

# Rate Limiting
variable "rate_limit_authenticated" {
  description = "Rate limit for authenticated requests (requests per second)"
  type        = number
  default     = 100
}

variable "rate_limit_unauthenticated" {
  description = "Rate limit for unauthenticated requests (requests per second)"
  type        = number
  default     = 5
}

variable "rate_limit_burst" {
  description = "Burst limit for rate limiting"
  type        = number
  default     = 200
}

# VPC Configuration (for VPC Link to ECS)
variable "vpc_id" {
  description = "VPC ID where ECS service runs"
  type        = string
}

variable "private_subnet_ids" {
  description = "Private subnet IDs for VPC Link"
  type        = list(string)
}

variable "ecs_security_group_id" {
  description = "Security group ID of the ECS service"
  type        = string
}

# Optional: Custom domain
variable "domain_name" {
  description = "Custom domain name for API Gateway (optional)"
  type        = string
  default     = ""
}

variable "certificate_arn" {
  description = "ACM certificate ARN for custom domain (required if domain_name is set)"
  type        = string
  default     = ""
}

# Tags
variable "tags" {
  description = "Tags to apply to all resources"
  type        = map(string)
  default     = {}
}
