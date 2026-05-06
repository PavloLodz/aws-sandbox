resource "azurerm_container_app_environment" "env" {
  name                       = "aws-attempt-env"
  location                   = azurerm_resource_group.rg.location
  resource_group_name        = azurerm_resource_group.rg.name
  log_analytics_workspace_id = azurerm_log_analytics_workspace.logs.id
}

resource "azurerm_container_app" "app" {
  name                         = "aws-attempt-app"
  container_app_environment_id = azurerm_container_app_environment.env.id
  resource_group_name          = azurerm_resource_group.rg.name
  revision_mode                = "Single"

  registry {
    server               = azurerm_container_registry.acr.login_server
    username             = azurerm_container_registry.acr.admin_username
    password_secret_name = "acr-password"
  }

  secret {
    name  = "acr-password"
    value = azurerm_container_registry.acr.admin_password
  }

  secret {
    name  = "neon-password"
    value = var.neon_password
  }

  template {
    min_replicas = 0
    max_replicas = 1

    container {
      name   = "app"
      image  = "${azurerm_container_registry.acr.login_server}/aws-attempt:latest"
      cpu    = 0.25
      memory = "0.5Gi"

      env {
        name  = "SPRING_PROFILES_ACTIVE"
        value = "azure"
      }
      env {
        name  = "NEON_HOST"
        value = var.neon_host
      }
      env {
        name  = "NEON_DB"
        value = var.neon_db
      }
      env {
        name  = "NEON_USER"
        value = var.neon_user
      }
      env {
        name        = "NEON_PASSWORD"
        secret_name = "neon-password"
      }
    }
  }

  ingress {
    external_enabled = true
    target_port      = 8080
    transport        = "http"
    traffic_weight {
      percentage      = 100
      latest_revision = true
    }
  }
}
