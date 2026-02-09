# IAM Role for Lambda
resource "aws_iam_role" "lambda" {
  name = "${var.name_prefix}-jwt-authorizer-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "lambda.amazonaws.com"
        }
      }
    ]
  })

  tags = var.tags
}

# Basic Lambda execution policy
resource "aws_iam_role_policy_attachment" "lambda_basic" {
  role       = aws_iam_role.lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

# CloudWatch Logs policy
resource "aws_iam_role_policy" "lambda_logging" {
  name = "${var.name_prefix}-jwt-authorizer-logging"
  role = aws_iam_role.lambda.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "arn:aws:logs:*:*:*"
      }
    ]
  })
}

# Package Lambda code
data "archive_file" "lambda" {
  type        = "zip"
  source_dir  = "${path.module}/../../../../lambda/jwt-authorizer/dist"
  output_path = "${path.module}/function.zip"
}

# Lambda Function
resource "aws_lambda_function" "authorizer" {
  filename         = data.archive_file.lambda.output_path
  function_name    = "${var.name_prefix}-jwt-authorizer"
  role             = aws_iam_role.lambda.arn
  handler          = "index.handlerV2"  # Use V2 handler for HTTP API
  source_code_hash = data.archive_file.lambda.output_base64sha256
  runtime          = "nodejs20.x"
  memory_size      = var.lambda_memory_mb
  timeout          = var.lambda_timeout

  environment {
    variables = {
      JWKS_URL        = var.jwks_url
      EXPECTED_ISSUER = var.jwt_issuer
      CACHE_TTL_MS    = tostring(var.cache_ttl_ms)
      GATEWAY_SECRET  = var.gateway_secret
    }
  }

  tags = var.tags
}

# CloudWatch Log Group
resource "aws_cloudwatch_log_group" "lambda" {
  name              = "/aws/lambda/${aws_lambda_function.authorizer.function_name}"
  retention_in_days = 14

  tags = var.tags
}
