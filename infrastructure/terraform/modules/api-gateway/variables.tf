variable "name_prefix" {
  description = "Prefix for resource names"
  type        = string
}

variable "environment" {
  description = "Environment name"
  type        = string
}

variable "user_service_url" {
  description = "URL of the user service"
  type        = string
}

variable "lambda_authorizer_arn" {
  description = "ARN of the Lambda authorizer function"
  type        = string
}

variable "lambda_authorizer_invoke_arn" {
  description = "Invoke ARN of the Lambda authorizer function"
  type        = string
}

# VPC Link configuration
variable "vpc_id" {
  description = "VPC ID for VPC Link"
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

# Rate limiting
variable "rate_limit_authenticated" {
  description = "Rate limit for authenticated requests per second"
  type        = number
  default     = 100
}

variable "rate_limit_unauthenticated" {
  description = "Rate limit for unauthenticated requests per second"
  type        = number
  default     = 5
}

variable "rate_limit_burst" {
  description = "Burst limit for rate limiting"
  type        = number
  default     = 200
}

# Custom domain
variable "domain_name" {
  description = "Custom domain name (optional)"
  type        = string
  default     = ""
}

variable "certificate_arn" {
  description = "ACM certificate ARN for custom domain"
  type        = string
  default     = ""
}

variable "tags" {
  description = "Tags to apply to resources"
  type        = map(string)
  default     = {}
}
