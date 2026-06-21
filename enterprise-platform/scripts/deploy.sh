#!/usr/bin/env bash
# =============================================================================
# deploy.sh — Manual deploy shortcut (bypasses ArgoCD)
#
# Use this only for dev environment one-off deployments.
# Production deployments always go through ArgoCD / GitOps.
#
# Usage: ./scripts/deploy.sh [dev|staging|prod]
# =============================================================================
set -euo pipefail

ENV="${1:-dev}"

if [[ "$ENV" == "prod" ]]; then
  echo "ERROR: Never deploy to prod manually. Use ArgoCD." >&2
  exit 1
fi

echo "Deploying to $ENV via Helm..."
helm upgrade --install myapp infrastructure/helm/myapp-chart \
  -f infrastructure/helm/myapp-chart/values-k8s.yaml \
  -f infrastructure/helm/myapp-chart/values-${ENV}.yaml \
  --namespace myapp \
  --create-namespace \
  --wait

echo "Done. Pods:"
kubectl get pods -n myapp
