output "db_endpoint" {
  description = "Hostname / address of the PostgreSQL instance."
  value       = db_instance_resource.main.address
}

output "db_port" {
  description = "Port the PostgreSQL instance listens on (5432)."
  value       = db_instance_resource.main.port
}

output "db_name" {
  description = "Name of the initial database created in the instance."
  value       = db_instance_resource.main.db_name
}

output "db_secret_arn" {
  description = "ARN of the secrets-manager secret that stores the full DB credentials JSON."
  value       = secret_resource.db_credentials.arn
}

# db_password is intentionally NOT an output.
# Callers retrieve credentials at runtime by reading the secret ARN above,
# keeping the generated password out of the Terraform output log entirely.
