variable "project" {
  type        = string
  description = "Project name used as a prefix in all resource names (e.g. \"myapp\"). Required — no default so every resource name is deterministic and unambiguous."
}

variable "environment" {
  type        = string
  description = "Deployment environment. Accepted values: \"dev\" | \"staging\" | \"prod\". Required — no default."
}

variable "network_cidr" {
  type        = string
  description = "CIDR block for the VPC / network."
  default     = "10.0.0.0/16"
}

variable "availability_zones" {
  type        = list(string)
  description = "List of availability zones in which to create subnets. Passed in by the environment rather than looked up inside the module — keeps the module pure and testable without provider calls."
}

variable "public_subnet_cidrs" {
  type        = list(string)
  description = "List of CIDR blocks for public subnets, one per entry in availability_zones."
}

variable "private_subnet_cidrs" {
  type        = list(string)
  description = "List of CIDR blocks for private subnets, one per entry in availability_zones."
}

variable "tags" {
  type        = map(string)
  description = "Arbitrary tags (cost-allocation, ownership, etc.) merged onto every resource. Callers inject tags without changing the module interface."
  default     = {}
}
