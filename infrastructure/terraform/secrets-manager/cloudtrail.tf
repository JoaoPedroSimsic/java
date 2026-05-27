locals {
  cloudtrail_name = coalesce(var.cloudtrail_name, "${var.app_name}-${var.environment}-secrets-audit")
}

resource "aws_cloudwatch_log_group" "cloudtrail" {
  name              = "/aws/cloudtrail/${local.cloudtrail_name}"
  retention_in_days = var.environment == "prod" ? 90 : 30

  tags = merge(var.tags, {
    Environment = var.environment
    Component   = "cloudtrail"
  })
}

resource "aws_s3_bucket" "cloudtrail" {
  count = var.cloudtrail_s3_bucket_name == null ? 1 : 0

  bucket        = "${var.app_name}-${var.environment}-cloudtrail-${local.account_id}"
  force_destroy = var.environment != "prod"

  tags = merge(var.tags, {
    Environment = var.environment
    Component   = "cloudtrail"
  })
}

resource "aws_s3_bucket_public_access_block" "cloudtrail" {
  count = var.cloudtrail_s3_bucket_name == null ? 1 : 0

  bucket                  = aws_s3_bucket.cloudtrail[0].id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "cloudtrail" {
  count = var.cloudtrail_s3_bucket_name == null ? 1 : 0

  bucket = aws_s3_bucket.cloudtrail[0].id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

locals {
  cloudtrail_bucket_name = coalesce(var.cloudtrail_s3_bucket_name, try(aws_s3_bucket.cloudtrail[0].id, null))
}

data "aws_iam_policy_document" "cloudtrail_bucket" {
  statement {
    sid    = "AWSCloudTrailAclCheck"
    effect = "Allow"
    principals {
      type        = "Service"
      identifiers = ["cloudtrail.amazonaws.com"]
    }
    actions   = ["s3:GetBucketAcl"]
    resources = ["arn:aws:s3:::${local.cloudtrail_bucket_name}"]
  }

  statement {
    sid    = "AWSCloudTrailWrite"
    effect = "Allow"
    principals {
      type        = "Service"
      identifiers = ["cloudtrail.amazonaws.com"]
    }
    actions   = ["s3:PutObject"]
    resources = ["arn:aws:s3:::${local.cloudtrail_bucket_name}/AWSLogs/${local.account_id}/*"]
    condition {
      test     = "StringEquals"
      variable = "s3:x-amz-acl"
      values   = ["bucket-owner-full-control"]
    }
  }
}

resource "aws_s3_bucket_policy" "cloudtrail" {
  count = var.cloudtrail_s3_bucket_name == null ? 1 : 0

  bucket = aws_s3_bucket.cloudtrail[0].id
  policy = data.aws_iam_policy_document.cloudtrail_bucket.json
}

resource "aws_iam_role" "cloudtrail_cloudwatch" {
  name = "${var.app_name}-${var.environment}-cloudtrail-logs"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        Service = "cloudtrail.amazonaws.com"
      }
      Action = "sts:AssumeRole"
    }]
  })
}

data "aws_iam_policy_document" "cloudtrail_cloudwatch" {
  statement {
    effect = "Allow"
    actions = [
      "logs:CreateLogStream",
      "logs:PutLogEvents",
    ]
    resources = ["${aws_cloudwatch_log_group.cloudtrail.arn}:*"]
  }
}

resource "aws_iam_role_policy" "cloudtrail_cloudwatch" {
  name   = "${var.app_name}-${var.environment}-cloudtrail-logs"
  role   = aws_iam_role.cloudtrail_cloudwatch.id
  policy = data.aws_iam_policy_document.cloudtrail_cloudwatch.json
}

resource "aws_cloudtrail" "secrets_audit" {
  name                          = local.cloudtrail_name
  s3_bucket_name                = local.cloudtrail_bucket_name
  include_global_service_events = true
  is_multi_region_trail         = true
  enable_log_file_validation    = true

  cloud_watch_logs_group_arn = "${aws_cloudwatch_log_group.cloudtrail.arn}:*"
  cloud_watch_logs_role_arn  = aws_iam_role.cloudtrail_cloudwatch.arn

  event_selector {
    read_write_type           = "All"
    include_management_events = true

    data_resource {
      type   = "AWS::SecretsManager::Secret"
      values = [for secret in aws_secretsmanager_secret.hermes : secret.arn]
    }
  }

  tags = merge(var.tags, {
    Environment = var.environment
    Component   = "cloudtrail"
  })
}

resource "aws_cloudwatch_log_metric_filter" "secretsmanager_get_secret_value" {
  name           = "${var.app_name}-${var.environment}-secretsmanager-get"
  log_group_name = aws_cloudwatch_log_group.cloudtrail.name
  pattern        = "{ ($.eventSource = \"secretsmanager.amazonaws.com\") && ($.eventName = \"GetSecretValue\") }"

  metric_transformation {
    name          = "${var.app_name}-${var.environment}-SecretsManagerGetSecretValue"
    namespace     = "Hermes/SecretsManager"
    value         = "1"
    default_value = "0"
  }
}

resource "aws_cloudwatch_metric_alarm" "secretsmanager_get_secret_value_spike" {
  alarm_name          = "${var.app_name}-${var.environment}-secretsmanager-get-spike"
  alarm_description   = "Unusually high Secrets Manager GetSecretValue volume (possible credential scraping)."
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "${var.app_name}-${var.environment}-SecretsManagerGetSecretValue"
  namespace           = "Hermes/SecretsManager"
  period              = 300
  statistic           = "Sum"
  threshold           = var.secretsmanager_getsecretvalue_alarm_threshold
  treat_missing_data  = "notBreaching"

  tags = merge(var.tags, {
    Environment = var.environment
    Component   = "cloudwatch-alarm"
  })
}
