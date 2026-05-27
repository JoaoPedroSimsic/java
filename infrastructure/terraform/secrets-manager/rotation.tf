locals {
  rotation_lambda_name = "${var.app_name}-${var.environment}-secrets-rotation"
  rotatable_secrets = {
    for key in var.rotation_secret_keys : key => local.all_secrets[key]
    if contains(keys(local.all_secrets), key)
  }
}

data "archive_file" "rotation_lambda" {
  type        = "zip"
  source_dir  = "${path.module}/../../lambda/secrets-rotation/dist"
  output_path = "${path.module}/../../lambda/secrets-rotation/function.zip"
}

resource "aws_iam_role" "rotation_lambda" {
  count = var.enable_automatic_rotation ? 1 : 0
  name  = "${local.rotation_lambda_name}-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = "lambda.amazonaws.com" }
    }]
  })

  tags = merge(var.tags, {
    Environment = var.environment
    Component   = "secrets-rotation"
  })
}

resource "aws_iam_role_policy_attachment" "rotation_lambda_basic" {
  count      = var.enable_automatic_rotation ? 1 : 0
  role       = aws_iam_role.rotation_lambda[0].name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

data "aws_iam_policy_document" "rotation_lambda" {
  count = var.enable_automatic_rotation ? 1 : 0

  statement {
    sid    = "RotateHermesSecrets"
    effect = "Allow"
    actions = [
      "secretsmanager:DescribeSecret",
      "secretsmanager:GetSecretValue",
      "secretsmanager:PutSecretValue",
      "secretsmanager:UpdateSecretVersionStage",
    ]
    resources = [for key in keys(local.rotatable_secrets) : aws_secretsmanager_secret.hermes[key].arn]
  }
}

resource "aws_iam_role_policy" "rotation_lambda" {
  count  = var.enable_automatic_rotation ? 1 : 0
  name   = "${local.rotation_lambda_name}-policy"
  role   = aws_iam_role.rotation_lambda[0].id
  policy = data.aws_iam_policy_document.rotation_lambda[0].json
}

resource "aws_lambda_function" "rotation" {
  count         = var.enable_automatic_rotation ? 1 : 0
  function_name = local.rotation_lambda_name
  role          = aws_iam_role.rotation_lambda[0].arn
  handler       = "index.handler"
  runtime       = "nodejs20.x"
  timeout       = 30
  filename      = data.archive_file.rotation_lambda.output_path
  source_code_hash = data.archive_file.rotation_lambda.output_base64sha256

  environment {
    variables = {
      AWS_REGION = var.aws_region
    }
  }

  tags = merge(var.tags, {
    Environment = var.environment
    Component   = "secrets-rotation"
  })
}

resource "aws_lambda_permission" "rotation" {
  for_each = var.enable_automatic_rotation ? local.rotatable_secrets : {}

  statement_id  = "AllowSecretsManagerInvoke-${each.key}"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.rotation[0].function_name
  principal     = "secretsmanager.amazonaws.com"
  source_arn    = aws_secretsmanager_secret.hermes[each.key].arn
}

resource "aws_secretsmanager_secret_rotation" "hermes" {
  for_each = var.enable_automatic_rotation ? local.rotatable_secrets : {}

  secret_id           = aws_secretsmanager_secret.hermes[each.key].id
  rotation_lambda_arn = aws_lambda_function.rotation[0].arn

  rotation_rules {
    automatically_after_days = var.rotation_schedule_days
  }

  depends_on = [aws_lambda_permission.rotation]
}

resource "aws_cloudwatch_event_rule" "rotation_succeeded" {
  count       = var.enable_automatic_rotation && var.rotation_notification_sns_topic_arn != null ? 1 : 0
  name        = "${var.app_name}-${var.environment}-secrets-rotation-succeeded"
  description = "Secrets Manager rotation succeeded (Phase D)"

  event_pattern = jsonencode({
    source      = ["aws.secretsmanager"]
    detail-type = ["AWS API Call via CloudTrail"]
    detail = {
      eventSource = ["secretsmanager.amazonaws.com"]
      eventName   = ["RotateSecret"]
    }
  })

  tags = merge(var.tags, {
    Environment = var.environment
    Component   = "secrets-rotation"
  })
}

resource "aws_cloudwatch_event_target" "rotation_succeeded_sns" {
  count     = var.enable_automatic_rotation && var.rotation_notification_sns_topic_arn != null ? 1 : 0
  rule      = aws_cloudwatch_event_rule.rotation_succeeded[0].name
  target_id = "sns"
  arn       = var.rotation_notification_sns_topic_arn

  input_transformer {
    input_paths = {
      secret = "$.detail.requestParameters.secretId"
      time   = "$.time"
    }
    input_template = "\"Hermes secret rotation completed: <secret> at <time>\""
  }
}
