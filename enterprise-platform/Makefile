# =============================================================================
# enterprise-platform — top-level Makefile
#
# Usage: make <target>
#        make help   — list all targets with descriptions
# =============================================================================

.DEFAULT_GOAL := help
.PHONY: help up down build k8s-dev k8s-staging k8s-prod k8s-delete \
        helm-install helm-upgrade helm-uninstall helm-lint \
        argocd-sync tf-init tf-plan tf-apply tf-destroy \
        ansible-deploy ansible-harden test clean

# Colours
CYAN  := \033[0;36m
RESET := \033[0m

help: ## Show this help message
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
	  | awk 'BEGIN {FS = ":.*?## "}; {printf "$(CYAN)%-25s$(RESET) %s\n", $$1, $$2}'

# -----------------------------------------------------------------------------
# Docker / local
# -----------------------------------------------------------------------------
up: ## Start app + PostgreSQL locally with Docker Compose
	docker compose -f application/docker-compose.yml up --build

down: ## Stop local environment and remove volumes
	docker compose -f application/docker-compose.yml down -v

build: ## Build the Docker image locally
	docker build -t myapp:local application/

# -----------------------------------------------------------------------------
# Kubernetes (minikube / raw manifests)
# -----------------------------------------------------------------------------
k8s-dev: ## Deploy to minikube using dev Kustomize overlay
	kubectl apply -k infrastructure/kubernetes/overlays/dev

k8s-staging: ## Deploy to staging using Kustomize overlay
	kubectl apply -k infrastructure/kubernetes/overlays/staging

k8s-prod: ## Deploy to prod using Kustomize overlay
	kubectl apply -k infrastructure/kubernetes/overlays/prod

k8s-delete: ## Remove dev deployment from cluster
	kubectl delete -k infrastructure/kubernetes/overlays/dev

# -----------------------------------------------------------------------------
# Helm
# -----------------------------------------------------------------------------
helm-lint: ## Lint the Helm chart
	helm lint infrastructure/helm/myapp-chart

helm-install: ## Install Helm chart on Kubernetes (dev)
	helm install myapp infrastructure/helm/myapp-chart \
	  -f infrastructure/helm/myapp-chart/values-k8s.yaml \
	  -f infrastructure/helm/myapp-chart/values-dev.yaml

helm-upgrade: ## Upgrade existing Helm release
	helm upgrade myapp infrastructure/helm/myapp-chart \
	  -f infrastructure/helm/myapp-chart/values-k8s.yaml \
	  -f infrastructure/helm/myapp-chart/values-dev.yaml

helm-uninstall: ## Uninstall Helm release
	helm uninstall myapp

# -----------------------------------------------------------------------------
# ArgoCD
# -----------------------------------------------------------------------------
argocd-sync: ## Force ArgoCD sync for dev app
	argocd app sync myapp-dev

argocd-status: ## Show ArgoCD app status
	argocd app get myapp-dev

# -----------------------------------------------------------------------------
# Terraform (ENV=dev|staging|prod)
# -----------------------------------------------------------------------------
ENV ?= dev

tf-init: ## Initialise Terraform for ENV (default: dev)
	terraform -chdir=infrastructure/terraform/environments/$(ENV) init

tf-plan: ## Plan Terraform changes for ENV
	terraform -chdir=infrastructure/terraform/environments/$(ENV) plan

tf-apply: ## Apply Terraform changes for ENV
	terraform -chdir=infrastructure/terraform/environments/$(ENV) apply

tf-destroy: ## Destroy Terraform resources for ENV
	terraform -chdir=infrastructure/terraform/environments/$(ENV) destroy

# -----------------------------------------------------------------------------
# Ansible
# -----------------------------------------------------------------------------
ansible-deploy: ## Run deploy playbook against dev inventory
	ansible-playbook infrastructure/ansible/playbooks/deploy.yml \
	  -i infrastructure/ansible/inventory/dev

ansible-harden: ## Run hardening playbook against dev inventory
	ansible-playbook infrastructure/ansible/playbooks/hardening.yml \
	  -i infrastructure/ansible/inventory/dev

# -----------------------------------------------------------------------------
# Application
# -----------------------------------------------------------------------------
test: ## Run all application tests (unit + integration)
	#./application/mvnw -f application/pom.xml clean verify
	mvn -f application/pom.xml clean verify

clean: ## Clean application build output
	mvn -f application/pom.xml clean
