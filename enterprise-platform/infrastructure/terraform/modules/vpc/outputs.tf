output "network_id" {
  description = "ID of the VPC / network resource."
  value       = network_resource.main.id
}

output "public_subnet_ids" {
  description = "List of public subnet IDs (one per availability zone)."
  value       = subnet_resource.public[*].id
}

output "private_subnet_ids" {
  description = "List of private subnet IDs (one per availability zone)."
  value       = subnet_resource.private[*].id
}

output "network_cidr" {
  description = "The CIDR block of the VPC / network."
  value       = network_resource.main.cidr_block
}
