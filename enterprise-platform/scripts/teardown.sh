#!/usr/bin/env bash
# =============================================================================
# teardown.sh — Destroy all resources for an environment
#
# WARNING: This removes cloud resources and will incur no further costs,
# but data will be lost. Always back up RDS before running against staging/prod.
#
# Usage: ./scripts/teardown.sh [dev|staging|prod]
# =============================================================================
set -euo pipefail

ENV="${1:-dev}"

echo "WARNING: This will destroy all $ENV resources."
read -r -p "Type the environment name to confirm: " CONFIRM

if [[ "$CONFIRM" != "$ENV" ]]; then
  echo "Aborted."
  exit 1
fi

echo "Uninstalling Helm release..."
helm uninstall myapp --namespace myapp 2>/dev/null || true

echo "Removing Kubernetes namespace..."
kubectl delete namespace myapp 2>/dev/null || true

echo "Destroying Terraform resources..."
terraform -chdir=infrastructure/terraform/environments/"$ENV" destroy -auto-approve

echo "Teardown complete for $ENV."
