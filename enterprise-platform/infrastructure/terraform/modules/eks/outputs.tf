output "cluster_name" {
  description = "Name of the managed Kubernetes cluster. Used to build kubeconfig and to reference the cluster in other modules."
  value       = kubernetes_cluster_resource.main.name
}

output "cluster_endpoint" {
  description = "API server endpoint URL of the cluster. Required for kubeconfig generation and provider configuration."
  value       = kubernetes_cluster_resource.main.endpoint
}

output "cluster_ca_cert" {
  description = "Base64-encoded certificate authority data for the cluster. Required to authenticate kubectl and the Kubernetes provider."
  value       = kubernetes_cluster_resource.main.certificate_authority
}

output "oidc_provider_arn" {
  description = "ARN / resource ID of the OIDC identity provider. Used when creating IAM role trust policies that allow pods to assume roles via workload identity."
  value       = oidc_provider_resource.main.arn
}

output "oidc_provider_url" {
  description = "Issuer URL of the OIDC provider (without https:// prefix where required by the provider). Used in IAM condition keys for scoped pod permissions."
  value       = oidc_provider_resource.main.url
}

output "node_role_arn" {
  description = "ARN of the IAM/service-account role attached to worker nodes. May be needed by add-ons that grant additional permissions to the node role."
  value       = iam_role_resource.node.arn
}
