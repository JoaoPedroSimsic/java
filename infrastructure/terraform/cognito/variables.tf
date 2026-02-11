variable "aws_region" {
  description = "AWS region for Cognito User Pool"
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Environment name (e.g., prod, staging)"
  type        = string
  default     = "prod"
}

variable "app_name" {
  description = "Application name used for resource naming"
  type        = string
  default     = "hermes"
}

variable "password_minimum_length" {
  description = "Minimum password length"
  type        = number
  default     = 8
}

variable "password_require_lowercase" {
  description = "Require lowercase letters in password"
  type        = bool
  default     = true
}

variable "password_require_uppercase" {
  description = "Require uppercase letters in password"
  type        = bool
  default     = true
}

variable "password_require_numbers" {
  description = "Require numbers in password"
  type        = bool
  default     = true
}

variable "password_require_symbols" {
  description = "Require symbols in password"
  type        = bool
  default     = true
}

variable "mfa_configuration" {
  description = "MFA configuration: OFF, ON, or OPTIONAL"
  type        = string
  default     = "OPTIONAL"
}

variable "access_token_validity" {
  description = "Access token validity in minutes"
  type        = number
  default     = 5
}

variable "id_token_validity" {
  description = "ID token validity in minutes"
  type        = number
  default     = 30
}

variable "refresh_token_validity" {
  description = "Refresh token validity in days"
  type        = number
  default     = 30
}

variable "callback_urls" {
  description = "Allowed callback URLs for OAuth2"
  type        = list(string)
  default = [
    "https://hermes-app.com/callback",
    "https://hermes-app.com/auth/callback"
  ]
}

variable "logout_urls" {
  description = "Allowed logout URLs"
  type        = list(string)
  default = [
    "https://hermes-app.com",
    "https://hermes-app.com/logout"
  ]
}

variable "cognito_domain_prefix" {
  description = "Prefix for the Cognito hosted domain (must be unique across all AWS accounts)"
  type        = string
  default     = "hermes-auth"
}

variable "github_client_id" {
  description = "GitHub OAuth App Client ID"
  type        = string
  sensitive   = true
}

variable "github_client_secret" {
  description = "GitHub OAuth App Client Secret"
  type        = string
  sensitive   = true
}

variable "tags" {
  description = "Tags to apply to all resources"
  type        = map(string)
  default = {
    Project   = "hermes"
    ManagedBy = "terraform"
  }
}
