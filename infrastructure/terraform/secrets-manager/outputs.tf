output "environment" {
  description = "Hermes environment."
  value       = var.environment
}

output "secret_prefix" {
  description = "AWS Secrets Manager name prefix for this environment."
  value       = local.secret_prefix
}

output "secret_names" {
  description = "Map of logical secret keys to AWS Secrets Manager secret names."
  value       = { for key, secret in aws_secretsmanager_secret.hermes : key => secret.name }
}

output "secret_arns" {
  description = "Map of logical secret keys to AWS Secrets Manager secret ARNs."
  value       = { for key, secret in aws_secretsmanager_secret.hermes : key => secret.arn }
}

output "eso_irsa_role_arn" {
  description = "IAM role ARN for External Secrets Operator (annotate ESO ServiceAccount)."
  value       = aws_iam_role.eso.arn
}

output "user_service_irsa_role_arn" {
  description = "IAM role ARN for user-service workload (annotate user-service ServiceAccount)."
  value       = aws_iam_role.user_service.arn
}

output "cloudtrail_name" {
  description = "CloudTrail trail name auditing Secrets Manager data events."
  value       = aws_cloudtrail.secrets_audit.name
}

output "cloudtrail_log_group_name" {
  description = "CloudWatch log group receiving CloudTrail Secrets Manager events."
  value       = aws_cloudwatch_log_group.cloudtrail.name
}

output "secretsmanager_get_alarm_name" {
  description = "CloudWatch alarm for anomalous GetSecretValue volume."
  value       = aws_cloudwatch_metric_alarm.secretsmanager_get_secret_value_spike.alarm_name
}

output "rotation_lambda_arn" {
  description = "ARN of the generic Secrets Manager rotation Lambda (null when rotation disabled)."
  value       = try(aws_lambda_function.rotation[0].arn, null)
}

output "rotated_secret_names" {
  description = "Secret names with automatic rotation enabled."
  value = var.enable_automatic_rotation ? [
    for key in keys(local.rotatable_secrets) : aws_secretsmanager_secret.hermes[key].name
  ] : []
}
