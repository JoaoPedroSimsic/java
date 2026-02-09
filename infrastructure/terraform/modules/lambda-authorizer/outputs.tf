output "function_arn" {
  description = "ARN of the Lambda function"
  value       = aws_lambda_function.authorizer.arn
}

output "function_name" {
  description = "Name of the Lambda function"
  value       = aws_lambda_function.authorizer.function_name
}

output "invoke_arn" {
  description = "Invoke ARN of the Lambda function"
  value       = aws_lambda_function.authorizer.invoke_arn
}

output "role_arn" {
  description = "ARN of the Lambda execution role"
  value       = aws_iam_role.lambda.arn
}
