terraform {
  required_version = ">= 1.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    archive = {
      source  = "hashicorp/archive"
      version = "~> 2.0"
    }
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = var.tags
  }
}

data "aws_caller_identity" "current" {}

data "aws_eks_cluster" "cluster" {
  name = var.eks_cluster_name
}

data "aws_iam_openid_connect_provider" "eks" {
  url = data.aws_eks_cluster.cluster.identity[0].oidc[0].issuer
}

locals {
  account_id         = data.aws_caller_identity.current.account_id
  secret_prefix      = "hermes/${var.environment}"
  hermes_namespace   = coalesce(var.hermes_kubernetes_namespace, "hermes-${var.environment}")
  eso_namespace      = var.eso_kubernetes_namespace
  oidc_provider_arn  = data.aws_iam_openid_connect_provider.eks.arn
  oidc_provider_host = replace(data.aws_iam_openid_connect_provider.eks.url, "https://", "")
}
