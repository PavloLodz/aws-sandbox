variable "project" {
  type        = string
  description = "Project name used as a prefix in all resource names. Required — no default."
}

variable "environment" {
  type        = string
  description = "Deployment environment. Accepted values: \"dev\" | \"staging\" | \"prod\". Required — no default."
}

variable "network_id" {
  type        = string
  description = "ID of the VPC / network. Used when creating the RDS security group."
}

variable "subnet_ids" {
  type        = list(string)
  description = "List of private subnet IDs for the DB subnet group. RDS must never be placed in public subnets."
}

variable "allowed_cidr_blocks" {
  type        = list(string)
  description = "CIDR blocks allowed to reach PostgreSQL port 5432. Pass the internal network CIDR only — never 0.0.0.0/0."
}

variable "db_name" {
  type        = string
  description = "Name of the initial database created in the PostgreSQL instance."
  default     = "myapp"
}

variable "db_instance_class" {
  type        = string
  description = "Instance class / machine type for the RDS instance."
  default     = "db.small"
}

variable "db_allocated_storage" {
  type        = number
  description = "Allocated storage size in GiB."
  default     = 20
}

variable "db_engine_version" {
  type        = string
  description = "PostgreSQL engine version."
  default     = "16"
}

variable "multi_az" {
  type        = bool
  description = "Enable Multi-AZ standby for high availability. Set to true in prod."
  default     = false
}

variable "deletion_protection" {
  type        = bool
  description = "Prevent the instance from being deleted via Terraform. Set to true in prod."
  default     = false
}

variable "skip_final_snapshot" {
  type        = bool
  description = "Skip the final snapshot on deletion. Set to false in prod to retain a snapshot before destroy."
  default     = true
}

variable "tags" {
  type        = map(string)
  description = "Arbitrary tags merged onto every resource."
  default     = {}
}
