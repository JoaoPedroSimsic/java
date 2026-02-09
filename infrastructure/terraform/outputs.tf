output "api_gateway_url" {
  description = "URL of the API Gateway endpoint"
  value       = module.api_gateway.api_url
}

output "api_gateway_id" {
  description = "ID of the API Gateway"
  value       = module.api_gateway.api_id
}

output "api_gateway_stage_name" {
  description = "Name of the API Gateway stage"
  value       = module.api_gateway.stage_name
}

output "lambda_authorizer_arn" {
  description = "ARN of the Lambda authorizer function"
  value       = module.lambda_authorizer.function_arn
}

output "lambda_authorizer_name" {
  description = "Name of the Lambda authorizer function"
  value       = module.lambda_authorizer.function_name
}

output "custom_domain_url" {
  description = "Custom domain URL (if configured)"
  value       = module.api_gateway.custom_domain_url
}
