# infrastructure/helm

Helm chart for deploying the application to Kubernetes and OpenShift.

## Structure

```
myapp-chart/
├── templates/     — Kubernetes/OpenShift resource templates
├── Chart.yaml     — chart metadata
├── values.yaml    — default values
├── values-dev.yaml       — dev environment overrides
├── values-k8s.yaml       — Kubernetes-specific values
└── values-openshift.yaml — OpenShift-specific values (Routes, SCC)
```

## Usage

```bash
# Validate
helm lint myapp-chart

# Preview rendered output
helm template myapp myapp-chart -f myapp-chart/values-k8s.yaml

# Install (Kubernetes)
helm install myapp myapp-chart -f myapp-chart/values-k8s.yaml

# Install (OpenShift)
helm install myapp myapp-chart -f myapp-chart/values-openshift.yaml

# Upgrade
helm upgrade myapp myapp-chart -f myapp-chart/values-k8s.yaml

# Roll back
helm rollback myapp 1

# Uninstall
helm uninstall myapp
```

## Important Notes

### Image Pull Policy (`image.pullPolicy: Never`)

The default `values.yaml` sets `image.pullPolicy: Never` because the chart is
configured for local development on **minikube**, where images are loaded directly
into the cluster's container runtime with `minikube image load` and do not need
to be pulled from a registry.

**This setting MUST be overridden for any non-local cluster:**

| Environment | Recommended `image.pullPolicy` |
|---|---|
| minikube (local) | `Never` (default) |
| CI / staging / prod | `IfNotPresent` |
| Always pull latest | `Always` |

Override at install time:
```bash
helm install myapp myapp-chart \
  -f myapp-chart/values-k8s.yaml \
  --set image.pullPolicy=IfNotPresent
```

Or set the correct value in your environment-specific values file (e.g.
`values-k8s.yaml`, `values-openshift.yaml`).

---

### Secrets (`secret.*` in `values.yaml`)

The `secret.*` block in `values.yaml` contains **plaintext development credentials
only**. These values exist solely to make local minikube development work out of
the box without additional setup.

**Never use `values.yaml` secret values in staging or production.**

For non-local deployments, supply secrets via one of these methods:

1. **`--set` flag at install time** (suitable for CI pipelines reading from a
   secrets store):
   ```bash
   helm install myapp myapp-chart \
     --set secret.dbPassword=$DB_PASSWORD \
     --set secret.postgresPassword=$PG_PASSWORD
   ```

2. **External Secrets Operator** (planned for Milestone 2/3) — secrets are
   fetched from a secrets manager (Vault, AWS Secrets Manager, etc.) and
   injected into Kubernetes `Secret` objects automatically.

3. **Sealed Secrets** — encrypt secrets at rest in Git using Bitnami Sealed
   Secrets; the in-cluster controller decrypts them at deploy time.

See the cross-cutting security requirements in
`application/prompts/requirements.md` for the full policy.

---

### PostgreSQL PVC Retention on `helm uninstall`

When you run `helm uninstall myapp`, the `PersistentVolumeClaim` created by
`templates/postgres-statefulset.yaml` is **not deleted**. This is intentional
Kubernetes behaviour — PVCs are excluded from Helm's garbage collection to
prevent accidental data loss.

**To fully clean up after uninstall (local dev only):**

```bash
helm uninstall myapp
kubectl delete pvc -n myapp -l app=postgres
# or by name:
kubectl delete pvc postgres-data-postgres-0 -n myapp
```

**Milestone 2 note:** A `post-delete` Helm hook will be considered to automate
PVC cleanup in non-production environments. In production, PVCs should never be
deleted automatically — data retention policies govern their lifecycle separately
from the application release.

---

### `managed-by: kubectl` Labels (Milestone 2)

All static template copies currently carry `managed-by: kubectl` in their labels
(inherited from the raw Kubernetes manifests in `infrastructure/kubernetes/base/`).

**Milestone 2** will replace these with `managed-by: helm` and add the standard
`helm.sh/chart` label via the `myapp.labels` named template defined in
`templates/_helpers.tpl`. This ensures Helm can correctly track and diff resources
it manages.

