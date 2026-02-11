# IAM Role for Lambda
resource "aws_iam_role" "github_auth_lambda" {
  name = "${var.app_name}-github-auth-lambda-role-${var.environment}"

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

# IAM Policy for Lambda to access Cognito
resource "aws_iam_role_policy" "github_auth_lambda_cognito" {
  name = "${var.app_name}-github-auth-cognito-policy"
  role = aws_iam_role.github_auth_lambda.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "cognito-idp:AdminCreateUser",
          "cognito-idp:AdminGetUser",
          "cognito-idp:AdminSetUserPassword",
          "cognito-idp:AdminInitiateAuth",
          "cognito-idp:AdminUpdateUserAttributes"
        ]
        Resource = aws_cognito_user_pool.main.arn
      }
    ]
  })
}

# Attach basic Lambda execution role
resource "aws_iam_role_policy_attachment" "github_auth_lambda_basic" {
  role       = aws_iam_role.github_auth_lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

# Lambda function
resource "aws_lambda_function" "github_auth" {
  filename         = "${path.module}/../../lambda/github-auth/function.zip"
  function_name    = "${var.app_name}-github-auth-${var.environment}"
  role             = aws_iam_role.github_auth_lambda.arn
  handler          = "index.handler"
  source_code_hash = filebase64sha256("${path.module}/../../lambda/github-auth/function.zip")
  runtime          = "nodejs20.x"
  timeout          = 30

  environment {
    variables = {
      USER_POOL_ID         = aws_cognito_user_pool.main.id
      CLIENT_ID            = aws_cognito_user_pool_client.main.id
      GITHUB_CLIENT_ID     = var.github_client_id
      GITHUB_CLIENT_SECRET = var.github_client_secret
      CALLBACK_URL         = "https://${aws_apigatewayv2_api.github_auth.id}.execute-api.${var.aws_region}.amazonaws.com/auth/github/callback"
      FRONTEND_URL         = var.frontend_url
    }
  }

  tags = var.tags

  depends_on = [
    aws_iam_role_policy_attachment.github_auth_lambda_basic,
    aws_iam_role_policy.github_auth_lambda_cognito
  ]
}

# Lambda permission for API Gateway
resource "aws_lambda_permission" "github_auth_api" {
  statement_id  = "AllowAPIGatewayInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.github_auth.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.github_auth.execution_arn}/*/*"
}
