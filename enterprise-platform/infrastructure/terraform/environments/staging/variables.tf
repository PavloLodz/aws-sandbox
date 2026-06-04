# environments/staging/variables.tf
#
# Variable declarations for the dev environment root module.
# All values are supplied via terraform.tfvars — see terraform.tfvars.example.
# Required variables have no default so Terraform errors clearly if they are missing.

# ── Identity ──────────────────────────────────────────────────────────────────

variable "project" {
  type        = string
  description = "Project name used as a prefix in all resource names (e.g. \"myapp\"). Required — no default so every resource name is deterministic and unambiguous."
}

variable "environment" {
  type        = string
  description = "Deployment environment. Accepted values: \"dev\" | \"staging\" | \"prod\". Required — no default."
}

variable "region" {
  type        = string
  description = "Provider region identifier (e.g. \"us-east-1\" for AWS, \"us-central1\" for GCP). Required — replace with the real region when a provider is chosen."
}

# ── Network ───────────────────────────────────────────────────────────────────

variable "network_cidr" {
  type        = string
  description = "CIDR block for the VPC / network. Required — supplied via tfvars."
}

variable "availability_zones" {
  type        = list(string)
  description = "List of availability zones in which to create subnets. Replace placeholder values with real zone names for the chosen provider. Required — supplied via tfvars."
}

variable "public_subnet_cidrs" {
  type        = list(string)
  description = "CIDR blocks for public subnets (one per AZ). Required — supplied via tfvars."
}

variable "private_subnet_cidrs" {
  type        = list(string)
  description = "CIDR blocks for private subnets (one per AZ). Required — supplied via tfvars."
}

# ── EKS / Kubernetes ──────────────────────────────────────────────────────────

variable "kubernetes_version" {
  type        = string
  description = "Kubernetes control-plane version. Pin to a specific minor version to control upgrade timing."
  default     = "1.30"
}

variable "node_instance_type" {
  type        = string
  description = "Instance type / machine type for EKS worker nodes. Provider-specific string — replace placeholder with a real type (e.g. \"t3.medium\" on AWS, \"e2-standard-2\" on GCP). Required — supplied via tfvars."
}

variable "node_min_size" {
  type        = number
  description = "Minimum number of nodes in the autoscaling group."
  default     = 1
}

variable "node_max_size" {
  type        = number
  description = "Maximum number of nodes in the autoscaling group."
  default     = 3
}

variable "node_desired_size" {
  type        = number
  description = "Desired node count at cluster creation / after terraform apply."
  default     = 1
}

# ── RDS / PostgreSQL ──────────────────────────────────────────────────────────

variable "db_instance_class" {
  type        = string
  description = "Instance class / machine type for the RDS instance. Provider-specific string — replace placeholder with a real class (e.g. \"db.t3.micro\" on AWS). Required — supplied via tfvars."
}

variable "db_allocated_storage" {
  type        = number
  description = "Allocated storage size in GiB for the PostgreSQL instance."
  default     = 20
}

variable "multi_az" {
  type        = bool
  description = "Enable Multi-AZ standby replica for high availability. Set true in prod. False is safe for dev/staging."
  default     = false
}

variable "deletion_protection" {
  type        = bool
  description = "Prevent accidental deletion of the database instance via Terraform. Always true in prod."
  default     = false
}

variable "skip_final_snapshot" {
  type        = bool
  description = "Skip the final automated snapshot when the instance is destroyed. Must be false in prod to retain a recovery point."
  default     = true
}
