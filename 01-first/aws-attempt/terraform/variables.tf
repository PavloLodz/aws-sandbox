variable "location" {
  description = "Azure region"
  default     = "West Europe"
}

variable "resource_group" {
  description = "Azure resource group name"
  default     = "aws-attempt-rg"
}

variable "acr_name" {
  description = "Azure Container Registry name (must be globally unique)"
  default     = "awsattemptacr"
}

variable "neon_host" {
  description = "Neon PostgreSQL host (e.g. ep-xxx.eu-west-2.aws.neon.tech)"
}

variable "neon_db" {
  description = "Neon database name"
  default     = "neondb"
}

variable "neon_user" {
  description = "Neon database username"
}

variable "neon_password" {
  description = "Neon database password"
  sensitive   = true
}
