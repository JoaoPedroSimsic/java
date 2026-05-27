variable "aws_region" {
  description = "AWS region for Secrets Manager, IAM, and CloudTrail."
  type        = string
}

variable "environment" {
  description = "Hermes environment name (staging or prod)."
  type        = string

  validation {
    condition     = contains(["staging", "prod"], var.environment)
    error_message = "environment must be staging or prod."
  }
}

variable "app_name" {
  description = "Application name prefix for IAM roles and alarms."
  type        = string
  default     = "hermes"
}

variable "eks_cluster_name" {
  description = "EKS cluster name used for IRSA trust policies."
  type        = string
}

variable "hermes_kubernetes_namespace" {
  description = "Kubernetes namespace for Hermes workloads (defaults to hermes-<environment>)."
  type        = string
  default     = null
}

variable "eso_kubernetes_namespace" {
  description = "Kubernetes namespace where External Secrets Operator runs."
  type        = string
  default     = "external-secrets"
}

variable "user_service_s3_bucket" {
  description = "S3 bucket for user-service profile images (IRSA scoped access)."
  type        = string
}

variable "seed_placeholder_secrets" {
  description = "When true, create placeholder secret versions (replace via seed script before workloads start)."
  type        = bool
  default     = false
}

variable "cloudtrail_name" {
  description = "Name for the CloudTrail trail auditing Secrets Manager API calls."
  type        = string
  default     = null
}

variable "cloudtrail_s3_bucket_name" {
  description = "S3 bucket for CloudTrail logs (must exist or be created separately)."
  type        = string
  default     = null
}

variable "secretsmanager_getsecretvalue_alarm_threshold" {
  description = "CloudWatch alarm threshold for GetSecretValue calls in 5 minutes (anomaly signal)."
  type        = number
  default     = 100
}

variable "tags" {
  description = "Tags applied to all resources."
  type        = map(string)
  default = {
    Project   = "hermes"
    ManagedBy = "terraform"
  }
}
