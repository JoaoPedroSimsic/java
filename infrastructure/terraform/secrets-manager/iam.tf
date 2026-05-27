data "aws_iam_policy_document" "eso_assume_role" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [local.oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.oidc_provider_host}:sub"
      values = [
        "system:serviceaccount:${local.eso_namespace}:external-secrets",
      ]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.oidc_provider_host}:aud"
      values   = ["sts.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "eso" {
  name               = "${var.app_name}-${var.environment}-eso"
  assume_role_policy = data.aws_iam_policy_document.eso_assume_role.json

  tags = merge(var.tags, {
    Environment = var.environment
    Component   = "external-secrets-operator"
  })
}

data "aws_iam_policy_document" "eso_read_secrets" {
  statement {
    sid    = "ReadHermesSecrets"
    effect = "Allow"
    actions = [
      "secretsmanager:GetSecretValue",
      "secretsmanager:DescribeSecret",
    ]
    resources = [for secret in aws_secretsmanager_secret.hermes : secret.arn]
  }

  statement {
    sid    = "ListSecretsForDiscovery"
    effect = "Allow"
    actions = [
      "secretsmanager:ListSecrets",
    ]
    resources = ["*"]
    condition {
      test     = "StringLike"
      variable = "secretsmanager:Name"
      values   = ["${local.secret_prefix}/*"]
    }
  }
}

resource "aws_iam_role_policy" "eso_read_secrets" {
  name   = "${var.app_name}-${var.environment}-eso-read-secrets"
  role   = aws_iam_role.eso.id
  policy = data.aws_iam_policy_document.eso_read_secrets.json
}

data "aws_iam_policy_document" "user_service_assume_role" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [local.oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.oidc_provider_host}:sub"
      values = [
        "system:serviceaccount:${local.hermes_namespace}:user-service",
      ]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.oidc_provider_host}:aud"
      values   = ["sts.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "user_service" {
  name               = "${var.app_name}-${var.environment}-user-service"
  assume_role_policy = data.aws_iam_policy_document.user_service_assume_role.json

  tags = merge(var.tags, {
    Environment = var.environment
    Component   = "user-service"
  })
}

data "aws_iam_policy_document" "user_service_s3" {
  statement {
    sid    = "ProfileImageObjectAccess"
    effect = "Allow"
    actions = [
      "s3:GetObject",
      "s3:PutObject",
      "s3:DeleteObject",
    ]
    resources = ["arn:aws:s3:::${var.user_service_s3_bucket}/*"]
  }

  statement {
    sid    = "ProfileImageBucketList"
    effect = "Allow"
    actions = [
      "s3:ListBucket",
    ]
    resources = ["arn:aws:s3:::${var.user_service_s3_bucket}"]
  }
}

resource "aws_iam_role_policy" "user_service_s3" {
  name   = "${var.app_name}-${var.environment}-user-service-s3"
  role   = aws_iam_role.user_service.id
  policy = data.aws_iam_policy_document.user_service_s3.json
}
