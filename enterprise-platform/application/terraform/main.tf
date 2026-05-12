terraform {
  required_version = ">= 1.6"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # Remote state — store tfstate in S3 so the team shares one source of truth.
  # Create the bucket manually once, then uncomment this block.
  # backend "s3" {
  #   bucket         = "your-tfstate-bucket"
  #   key            = "aws-attempt/${var.environment}/terraform.tfstate"
  #   region         = var.aws_region
  #   dynamodb_table = "terraform-locks"
  #   encrypt        = true
  # }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "aws-attempt"
      Environment = var.environment
      ManagedBy   = "Terraform"
    }
  }
}

# ── Networking ────────────────────────────────────────────────────────────────
module "networking" {
  source = "./modules/networking"

  environment        = var.environment
  vpc_cidr           = var.vpc_cidr
  availability_zones = var.availability_zones
}

# ── S3 bucket for application file uploads ────────────────────────────────────
module "s3" {
  source = "./modules/s3"

  environment = var.environment
  bucket_name = var.s3_bucket_name
}

# ── RDS PostgreSQL ────────────────────────────────────────────────────────────
module "rds" {
  source = "./modules/rds"

  environment        = var.environment
  vpc_id             = module.networking.vpc_id
  private_subnet_ids = module.networking.private_subnet_ids
  ec2_sg_id          = module.ec2.app_sg_id

  db_name     = var.db_name
  db_username = var.db_username
  db_password = var.db_password
}

# ── EC2 application server ────────────────────────────────────────────────────
module "ec2" {
  source = "./modules/ec2"

  environment       = var.environment
  vpc_id            = module.networking.vpc_id
  public_subnet_id  = module.networking.public_subnet_ids[0]
  key_pair_name     = var.key_pair_name
  instance_type     = var.ec2_instance_type
  s3_bucket_arn     = module.s3.bucket_arn
}
