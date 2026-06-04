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
  description = "ID of the VPC / network in which the cluster is placed. Supplied by the vpc module output."
}

variable "subnet_ids" {
  type        = list(string)
  description = "List of private subnet IDs for worker nodes. Supplied by vpc module's private_subnet_ids output."
}

variable "kubernetes_version" {
  type        = string
  description = "Kubernetes version for the managed cluster control plane."
  default     = "1.30"
}

variable "node_instance_type" {
  type        = string
  description = "Instance / machine type for worker nodes (provider-agnostic placeholder; replace with provider-specific type on adoption)."
  default     = "standard-2vcpu-4gb"
}

variable "node_min_size" {
  type        = number
  description = "Minimum number of worker nodes in the node group."
  default     = 1
}

variable "node_max_size" {
  type        = number
  description = "Maximum number of worker nodes in the node group."
  default     = 5
}

variable "node_desired_size" {
  type        = number
  description = "Desired (initial) number of worker nodes in the node group."
  default     = 2
}

variable "tags" {
  type        = map(string)
  description = "Arbitrary tags merged onto every resource."
  default     = {}
}
