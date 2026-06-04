output "registry_url" {
  description = "Full URL of the container image registry (used in docker push / Kubernetes image references)."
  value       = registry_resource.main.repository_url
}

output "registry_arn" {
  description = "ARN of the container image registry."
  value       = registry_resource.main.arn
}
