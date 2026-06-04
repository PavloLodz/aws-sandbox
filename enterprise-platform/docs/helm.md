# Helm — Topic 3

How to lint, install, upgrade, inspect, diff, roll back, and tear down the
`myapp` Helm release on minikube (dev) and production Kubernetes clusters.

All commands assume you are at the repository root. The release name is
`myapp` and the namespace is `myapp` throughout.

---

## Prerequisites

Confirm all of the following before running any helm command:

```bash
# Confirm Helm 3 is installed
helm version

# Confirm the cluster is reachable
kubectl cluster-info

# Start minikube with enough resources for all pods
minikube start --cpus=4 --memory=6144

# Enable the nginx ingress controller (required for dev and k8s value files)
minikube addons enable ingress

# Enable metrics-server (required for HPA CPU autoscaling)
minikube addons enable metrics-server

# Build the app image and load it into minikube's internal image store
# values.yaml sets imagePullPolicy: Never — the image must exist locally
cd application
docker build -t myapp:local .
minikube image load myapp:local
minikube image ls | grep myapp    # confirm the image is visible inside minikube

# Add the dev hostname to /etc/hosts (run once — sudo required)
echo "$(minikube ip)  myapp.dev.local" | sudo tee -a /etc/hosts
```

`imagePullPolicy: Never` (set in the base `values.yaml`) means Kubernetes
will not attempt to pull the image from any registry — it must already exist
inside minikube's image store. Forgetting `minikube image load` is the most
common cause of `ErrImageNeverPull` on a first install. The `values-k8s.yaml`
override changes this to `IfNotPresent` for production where the image is
pulled from a real registry.

---

## Lint

`helm lint` validates template syntax, required `Chart.yaml` fields, and
schema compliance entirely client-side with no cluster connection. Run it
before every install or upgrade, and before committing any chart change.

```bash
# Validate chart structure against base values only
helm lint infrastructure/helm/myapp-chart

# Validate each environment combination — all four must exit 0
helm lint infrastructure/helm/myapp-chart \
  -f infrastructure/helm/myapp-chart/values-dev.yaml

helm lint infrastructure/helm/myapp-chart \
  -f infrastructure/helm/myapp-chart/values-k8s.yaml

helm lint infrastructure/helm/myapp-chart \
  -f infrastructure/helm/myapp-chart/values-openshift.yaml
```

Expected output:

```
==> Linting infrastructure/helm/myapp-chart
[INFO] Chart.yaml: icon is recommended

1 chart(s) linted, 0 chart(s) failed
```

The `[INFO]` line about the icon is informational. The target is
`0 chart(s) failed`. Any `[WARNING]` or `[ERROR]` line is a real problem
and must be fixed before proceeding. Running lint against all four value
files matters because a syntax error inside a conditional (e.g.
`{{- if .Values.openshift }}`) is only triggered when that flag is in
scope — validating base values alone will not catch it. The CI workflow
`ci/github-actions/helm-lint-template.yml` runs all four combinations
automatically on every pull request.

---

## Template and Dry-run

`helm template` renders manifests entirely client-side with no cluster
connection. `--dry-run` sends rendered manifests to the cluster's API server
for schema validation without applying them. Each catches a different class
of error — use both before upgrading a production cluster.

```bash
# ── Client-side render — no cluster required ─────────────────────────────────
# Renders all manifests to stdout. Safe to run offline. Used in CI.
helm template myapp infrastructure/helm/myapp-chart \
  -f infrastructure/helm/myapp-chart/values-dev.yaml

# Render a single template — useful when debugging one specific resource
helm template myapp infrastructure/helm/myapp-chart \
  -f infrastructure/helm/myapp-chart/values-openshift.yaml \
  -s templates/route.yaml

# Assert the OpenShift render: Route present, Ingress absent
helm template myapp infrastructure/helm/myapp-chart \
  -f infrastructure/helm/myapp-chart/values-openshift.yaml | grep "kind:"
# expected: kind: Route (no kind: Ingress in output)

# ── Server-side dry-run — requires a running cluster ─────────────────────────
# Validates rendered manifests against the cluster's live API schemas.
# Catches deprecated API versions, CRD schema violations, field type errors.
helm upgrade --install myapp infrastructure/helm/myapp-chart \
  -f infrastructure/helm/myapp-chart/values-dev.yaml \
  --namespace myapp --create-namespace \
  --dry-run --debug
```

When added to any Helm command, `--debug` prints the fully merged values and
all rendered manifests before the cluster sees them. It is the most effective
flag for tracing why a value is not rendering as expected — paste the output
into an editor and search for the field under investigation.

| Scenario | Tool |
|---|---|
| Offline, CI, pre-merge validation | `helm template` |
| Debugging a single template file | `helm template -s templates/<file>` |
| Asserting which kinds render per environment | `helm template \| grep "kind:"` |
| Final check before upgrading a production cluster | `helm upgrade --dry-run --debug` |

---

## Installing

`helm upgrade --install` is used throughout rather than bare `helm install`.
It is idempotent: it installs the release on the first run and upgrades it on
every subsequent run, avoiding the `Error: cannot re-use a name that is still
in use` failure that bare `helm install` produces when a release already exists.

```bash
# ── Dev — local minikube ──────────────────────────────────────────────────────
# 1 replica, DEBUG logging, imagePullPolicy: Never, Ingress at myapp.dev.local
helm upgrade --install myapp infrastructure/helm/myapp-chart \
  -f infrastructure/helm/myapp-chart/values-dev.yaml \
  --namespace myapp --create-namespace

# ── Production Kubernetes ─────────────────────────────────────────────────────
# 3 replicas, WARN logging, IfNotPresent pull policy, ServiceMonitor enabled
helm upgrade --install myapp infrastructure/helm/myapp-chart \
  -f infrastructure/helm/myapp-chart/values-k8s.yaml \
  --namespace myapp --create-namespace

# ── OpenShift / CRC (Topic 10) ────────────────────────────────────────────────
# Route enabled, Ingress disabled, runAsNonRoot: true (no runAsUser)
helm upgrade --install myapp infrastructure/helm/myapp-chart \
  -f infrastructure/helm/myapp-chart/values-openshift.yaml \
  --namespace myapp --create-namespace
```

The chart includes `templates/namespace.yaml` which manages the `myapp`
namespace as a Helm resource. The `--create-namespace` flag is an additional
safety net for out-of-order operations — for example, if `helm upgrade
--install` runs before ArgoCD has synced the namespace resource. It is a no-op
when the namespace already exists and is safe to pass on every run.

---

## Verifying

```bash
# Confirm the release is in DEPLOYED state
helm list -n myapp

# Watch pods start — postgres-0 must be Running before myapp pods become Ready
kubectl get pods -n myapp -w

# Show the NOTES.txt output — prints the active URL for the current environment
helm status myapp -n myapp

# End-to-end smoke test through the Ingress (dev environment)
curl -s http://myapp.dev.local/actuator/health
# expected: {"status":"UP"}

curl -s http://myapp.dev.local/api/items
# expected: [] on a fresh database (HTTP 200)

# Swagger UI
curl -s -o /dev/null -w "%{http_code}" http://myapp.dev.local/swagger-ui.html
# expected: 302 (redirect to swagger-ui/index.html)
```

---

## Upgrading

Rolling out a new image tag is the primary day-2 operation. The commands below
trigger a zero-downtime rolling update — the mechanism is explained after the
code block, not just the syntax.

```bash
# Roll out a new image tag — triggers a zero-downtime rolling update
helm upgrade myapp infrastructure/helm/myapp-chart \
  -f infrastructure/helm/myapp-chart/values-k8s.yaml \
  --set image.tag=abc123 \
  --namespace myapp

# Watch the rolling update complete before returning
kubectl rollout status deployment/myapp -n myapp
```

The Deployment template sets `maxUnavailable: 0` and `maxSurge: 1`. Kubernetes
starts one new pod with the updated image and waits for it to pass its readiness
probe (`GET /actuator/health/readiness`, `initialDelaySeconds: 30`). Only after
the new pod is `Ready` does Kubernetes terminate one old pod. This repeats until
all replicas are on the new version. At no point is a request routed to a pod
that has not passed its readiness check.

```bash
# Raise log level temporarily — no image change, only ConfigMap re-applied
helm upgrade myapp infrastructure/helm/myapp-chart \
  -f infrastructure/helm/myapp-chart/values-k8s.yaml \
  --set config.loggingLevel=DEBUG \
  --namespace myapp
```

`--set` overrides apply only to that one command and are not persisted anywhere.
The next `helm upgrade` that omits the same `--set` flag will revert the value
to whatever the value file contains. Use `--set` only for ephemeral overrides
like image tags or temporary debug flags. All stable environment configuration
belongs in the versioned value files.

---

## Diff

`helm diff` (the `helm-diff` plugin) shows exactly which Kubernetes resource
fields will change before an upgrade runs — the Helm equivalent of
`terraform plan`. Run it before every production upgrade to prevent surprises.

```bash
# Install the plugin once per workstation
helm plugin install https://github.com/databus23/helm-diff

# Preview exactly what will change before upgrading
helm diff upgrade myapp infrastructure/helm/myapp-chart \
  -f infrastructure/helm/myapp-chart/values-k8s.yaml \
  --set image.tag=abc123 \
  --namespace myapp
```

Reading the output:

- Green lines (`+`) are fields being added
- Red lines (`-`) are fields being removed
- Grey lines are unchanged context shown for reference

For an image-tag upgrade the diff should show only the `image:` field changing
under the Deployment's container spec. If the diff also shows unexpected changes
to `replicas`, `resources`, or labels, the value file has drifted from what was
last applied — investigate before proceeding.

---

## Rollback

`helm rollback` re-applies the manifests from a previous release revision. Helm
stores each revision as a Kubernetes Secret in the release namespace, so rollback
history is available for as long as those Secrets exist.

```bash
# List all revisions for the release before deciding which to target
helm history myapp -n myapp
# Columns: REVISION | UPDATED | STATUS | CHART | APP VERSION | DESCRIPTION

# Roll back to the immediately previous revision (most common)
helm rollback myapp -n myapp

# Roll back to a specific revision number (from helm history output)
helm rollback myapp 1 -n myapp

# Watch pod recovery — rollback triggers the same rolling update mechanism
kubectl rollout status deployment/myapp -n myapp
```

`helm rollback` re-applies the full set of rendered manifests from the target
revision, triggering Kubernetes' standard rolling update — `maxUnavailable: 0`
is honoured during rollback exactly as during an upgrade. The reverted image is
served from the node's local image cache (`IfNotPresent`) if it was previously
pulled, making rollback fast even without registry access.

`helm rollback` rolls back the Helm release — the Kubernetes resources and
configuration. It does **not** roll back the database schema. If the release
being rolled back introduced a Flyway migration (a new table, column, or index),
that migration remains in the database after rollback. The rolled-back application
version must be schema-compatible with the current database state or it will fail
to start. Always design Flyway migrations to be backwards-compatible with the
previous application version before deploying to any shared environment.

---

## Inspecting a Release

Day-2 commands for understanding the exact current state of a live release.

```bash
# Show the values that were applied (overrides only, not chart defaults)
helm get values myapp -n myapp

# Show all values including chart defaults
helm get values myapp -n myapp --all

# Show the full rendered manifests of the current live release
helm get manifest myapp -n myapp

# Show release status and reprint the NOTES.txt output
helm status myapp -n myapp

# Show complete revision history
helm history myapp -n myapp
```

- `helm get values --all` reveals what was actually in effect when a release
  was deployed. Compare with the value files in Git to detect drift.
- `helm get manifest` shows the YAML Helm sent to Kubernetes after all template
  functions, conditionals, and value merges resolved. Compare with
  `kubectl get <resource> -o yaml` to see whether Kubernetes or an admission
  webhook mutated the resource after it was applied.
- `helm history` is the first command to run before a rollback — it shows the
  revision list with status and description so you know which revision number
  to target.

---

## Teardown

```bash
# Remove all Helm-managed resources for the release
helm uninstall myapp -n myapp

# PersistentVolumeClaims are NOT removed by helm uninstall — by design
# Remove them explicitly only after confirming data is no longer needed
kubectl delete pvc -l app=myapp -n myapp
kubectl delete pvc -l app=postgres -n myapp

# Remove the namespace entirely (fastest complete teardown)
kubectl delete namespace myapp

# Alternative: uninstall but preserve revision history for potential rollback
helm uninstall myapp -n myapp --keep-history
```

Helm does not delete PersistentVolumeClaims by default because a PVC holds an
actual data volume — accidental deletion is unrecoverable. This is the correct
default for stateful workloads like PostgreSQL. Delete PVCs manually only after
explicitly confirming the data is no longer needed.

`--keep-history` preserves the release revision Secrets in the namespace after
uninstall. This allows `helm rollback` to restore the release even after it has
been uninstalled. Use this flag when removing a release temporarily — for example,
to free cluster capacity — without losing the ability to restore from history.

---

## Helm Commands Reference

A dense reference for day-to-day use — mirrors the Resource Types Reference
table at the end of `docs/kubernetes.md`.

| Command | Purpose |
|---|---|
| `helm version` | Confirm Helm 3 is installed |
| `helm lint ./myapp-chart` | Validate chart structure and templates (no cluster) |
| `helm lint ./myapp-chart -f values-dev.yaml` | Validate with a specific environment overlay |
| `helm template myapp ./myapp-chart -f values-dev.yaml` | Render all manifests client-side (no cluster) |
| `helm template myapp ./myapp-chart -s templates/route.yaml -f values-openshift.yaml` | Render a single template file |
| `helm template myapp ./myapp-chart -f values-openshift.yaml \| grep "kind:"` | Assert which resource kinds render per environment |
| `helm upgrade --install myapp ./myapp-chart -f values-dev.yaml -n myapp --create-namespace` | Idempotent install or upgrade — dev |
| `helm upgrade --install myapp ./myapp-chart -f values-k8s.yaml -n myapp --create-namespace` | Idempotent install or upgrade — production |
| `helm upgrade --install myapp ./myapp-chart -f values-openshift.yaml -n myapp --create-namespace` | Idempotent install or upgrade — OpenShift |
| `helm list -n myapp` | List releases in the namespace |
| `helm status myapp -n myapp` | Show release status and NOTES.txt output |
| `helm upgrade myapp ./myapp-chart -f values-k8s.yaml --set image.tag=abc123 -n myapp` | Roll out a new image tag |
| `kubectl rollout status deployment/myapp -n myapp` | Watch rolling update progress |
| `helm plugin install https://github.com/databus23/helm-diff` | Install helm-diff plugin (once per workstation) |
| `helm diff upgrade myapp ./myapp-chart -f values-k8s.yaml --set image.tag=abc123 -n myapp` | Preview changes before upgrading |
| `helm history myapp -n myapp` | Show all revisions for the release |
| `helm rollback myapp -n myapp` | Revert to the previous revision |
| `helm rollback myapp 1 -n myapp` | Revert to a specific revision number |
| `helm get values myapp -n myapp` | Show the values applied to the current release |
| `helm get values myapp -n myapp --all` | Show all values including chart defaults |
| `helm get manifest myapp -n myapp` | Show the rendered manifests of the current release |
| `helm uninstall myapp -n myapp` | Remove all Helm-managed resources |
| `helm uninstall myapp -n myapp --keep-history` | Remove resources but preserve revision history |
