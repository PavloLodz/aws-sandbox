# environments/dev/main.tf
#
# Environment root module for dev.
# Wires the four reusable modules (vpc, eks, rds, ecr) together with dev-appropriate values.
# All variable values are supplied via terraform.tfvars — see terraform.tfvars.example.

terraform {
  required_version = ">= 1.8"

  required_providers {
    # Replace this block with the chosen provider when cloud deployment begins.
    # The module calls below do not change — only this provider block changes.
    random {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
    # Example AWS:    aws { source = "hashicorp/aws", version = "~> 5.0" }
    # Example GCP:    google { source = "hashicorp/google", version = "~> 5.0" }
    # Example Azure:  azurerm { source = "hashicorp/azurerm", version = "~> 3.0" }
  }
}

# provider block goes here — omitted until provider is chosen

# ── Shared tags applied to every resource created by this environment ──────────

locals {
  common_tags = {
    project     = var.project
    environment = var.environment
    managed_by  = "terraform"
  }
}

# ── VPC / Network ─────────────────────────────────────────────────────────────

module "vpc" {
  source = "../../modules/vpc"

  project              = var.project
  environment          = var.environment
  network_cidr         = var.network_cidr
  availability_zones   = var.availability_zones
  public_subnet_cidrs  = var.public_subnet_cidrs
  private_subnet_cidrs = var.private_subnet_cidrs
  tags                 = local.common_tags
}

# ── EKS / Kubernetes Cluster ──────────────────────────────────────────────────

module "eks" {
  source = "../../modules/eks"

  project            = var.project
  environment        = var.environment
  network_id         = module.vpc.network_id         # wires VPC output to cluster input
  subnet_ids         = module.vpc.private_subnet_ids # nodes run in private subnets only
  kubernetes_version = var.kubernetes_version
  node_instance_type = var.node_instance_type
  node_min_size      = var.node_min_size
  node_max_size      = var.node_max_size
  node_desired_size  = var.node_desired_size
  tags               = local.common_tags
}

# ── RDS / PostgreSQL ──────────────────────────────────────────────────────────

module "rds" {
  source = "../../modules/rds"

  project              = var.project
  environment          = var.environment
  network_id           = module.vpc.network_id
  subnet_ids           = module.vpc.private_subnet_ids
  allowed_cidr_blocks  = [module.vpc.network_cidr] # internal network CIDR only — never 0.0.0.0/0
  db_instance_class    = var.db_instance_class
  db_allocated_storage = var.db_allocated_storage
  multi_az             = var.multi_az
  deletion_protection  = var.deletion_protection
  skip_final_snapshot  = var.skip_final_snapshot
  tags                 = local.common_tags
}

# ── ECR / Container Registry ──────────────────────────────────────────────────

module "ecr" {
  source = "../../modules/ecr"

  project     = var.project
  environment = var.environment
  tags        = local.common_tags
}

# ── Outputs — visible after terraform apply ────────────────────────────────────

output "cluster_endpoint" {
  description = "API server endpoint of the Kubernetes cluster."
  value       = module.eks.cluster_endpoint
}

output "db_endpoint" {
  description = "Hostname of the PostgreSQL instance."
  value       = module.rds.db_endpoint
}

output "db_secret_arn" {
  description = "ARN of the secrets-manager secret holding full DB credentials JSON."
  value       = module.rds.db_secret_arn
}

output "registry_url" {
  description = "Full URL of the container image registry."
  value       = module.ecr.registry_url
}
