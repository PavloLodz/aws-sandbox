output "app_url" {
  description = "Public URL of the deployed Container App"
  value       = "https://${azurerm_container_app.app.latest_revision_fqdn}"
}

output "acr_login_server" {
  description = "ACR login server for docker push"
  value       = azurerm_container_registry.acr.login_server
}
