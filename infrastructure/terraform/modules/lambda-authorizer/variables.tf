variable "name_prefix" {
  description = "Prefix for resource names"
  type        = string
}

variable "jwks_url" {
  description = "URL of the JWKS endpoint"
  type        = string
}

variable "jwt_issuer" {
  description = "Expected JWT issuer"
  type        = string
}

variable "cache_ttl_ms" {
  description = "Cache TTL for JWKS in milliseconds"
  type        = number
  default     = 900000  # 15 minutes
}

variable "lambda_memory_mb" {
  description = "Memory allocation for Lambda function"
  type        = number
  default     = 256
}

variable "lambda_timeout" {
  description = "Timeout for Lambda function in seconds"
  type        = number
  default     = 10
}

variable "gateway_secret" {
  description = "Secret shared between API Gateway and backend services"
  type        = string
  sensitive   = true
}

variable "tags" {
  description = "Tags to apply to resources"
  type        = map(string)
  default     = {}
}
