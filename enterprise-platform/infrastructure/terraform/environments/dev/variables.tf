variable "project" {
  type    = string
  default = "myapp"
}

variable "environment" {
  type    = string
  default = "dev"
}

variable "region" {
  type    = string
  default = "us-east-1" # override per provider/region when adopted
}

# ── Network ───────────────────────────────────────────────────────────────────

variable "network_cidr" {
  type        = string
  description = "CIDR block for the VPC/network. Supplied via tfvars — no default."
}

variable "availability_zones" {
  type        = list(string)
  description = "List of availability zones to spread subnets across. Supplied via tfvars."
}

variable "public_subnet_cidrs" {
  type        = list(string)
  description = "CIDR blocks for public subnets (one per AZ). Supplied via tfvars."
}

variable "private_subnet_cidrs" {
  type        = list(string)
  description = "CIDR blocks for private subnets (one per AZ). Supplied via tfvars."
}

# ── EKS ───────────────────────────────────────────────────────────────────────

variable "kubernetes_version" {
  type    = string
  default = "1.30"
}

variable "node_instance_type" {
  type        = string
  description = "EC2 instance type for the EKS node group. Supplied via tfvars."
}

variable "node_min_size" {
  type        = number
  description = "Minimum number of nodes in the node group autoscaler. Supplied via tfvars."
}

variable "node_max_size" {
  type        = number
  description = "Maximum number of nodes in the node group autoscaler. Supplied via tfvars."
}

variable "node_desired_size" {
  type        = number
  description = "Desired number of nodes at cluster creation. Supplied via tfvars."
}

# ── RDS ───────────────────────────────────────────────────────────────────────

variable "db_instance_class" {
  type        = string
  description = "Instance class / machine type for the RDS instance. Supplied via tfvars."
}

variable "db_allocated_storage" {
  type        = number
  description = "Allocated storage size in GiB. Supplied via tfvars."
}

variable "multi_az" {
  type        = bool
  description = "Enable Multi-AZ standby for high availability. Supplied via tfvars."
}

variable "deletion_protection" {
  type        = bool
  description = "Prevent the instance from being deleted via Terraform. Supplied via tfvars."
}

variable "skip_final_snapshot" {
  type        = bool
  description = "Skip the final snapshot on deletion. Supplied via tfvars."
}
