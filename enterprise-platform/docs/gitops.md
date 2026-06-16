# GitOps — Topic 6

Operational runbook for the ArgoCD GitOps delivery pipeline in the
`gitops/` and `infrastructure/argocd/` directories. ArgoCD watches the
`gitops/` directory and continuously reconciles the cluster to match whatever
is committed there — no `kubectl apply` ever runs in CI. The cluster state
is always derived from Git.

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [ArgoCD Installation](#2-argocd-installation)
3. [Initial Setup](#3-initial-setup)
4. [GitOps Workflow (end-to-end)](#4-gitops-workflow-end-to-end)
5. [Application Reference](#5-application-reference)
6. [RBAC Reference](#6-rbac-reference)
7. [AppProject Reference](#7-appproject-reference)
8. [Sync Operations](#8-sync-operations)
9. [Troubleshooting](#9-troubleshooting)
10. [ArgoCD Commands Reference](#10-argocd-commands-reference)

---

## 1. Prerequisites

Confirm all required tools are present before running any ArgoCD command.

| Tool | Minimum version | Check command |
|---|---|---|
| ArgoCD | ≥ 2.9 | `argocd version` |
| kubectl | ≥ 1.27 | `kubectl version --client` |
| minikube | ≥ 1.32 | `minikube version` |

```bash
# Confirm argocd CLI is installed
argocd version

# Confirm kubectl is configured for the minikube context
kubectl cluster-info
kubectl config current-context   # should print: minikube
```

**Required minikube addon:**

```bash
# nginx ingress must be enabled — argocd.local Ingress depends on it
minikube addons enable ingress
minikube addons list | grep ingress   # confirm: STATUS = enabled
```

**One-time /etc/hosts entry** (required per developer machine):

```bash
# argocd.local must resolve to the minikube VM IP
echo "$(minikube ip)  argocd.local" | sudo tee -a /etc/hosts
```

---

## 2. ArgoCD Installation

Run these commands once per cluster. Skip if ArgoCD is already installed.

```bash
# Create the argocd namespace
kubectl create namespace argocd

# Install ArgoCD — stable manifest from the official release
kubectl apply -n argocd -f \
  https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

# Wait for the API server to become available (up to 120 seconds)
kubectl wait --for=condition=available deployment/argocd-server \
  -n argocd --timeout=120s

# Enable nginx ingress addon if not already done
minikube addons enable ingress

# Add argocd.local to /etc/hosts
echo "$(minikube ip)  argocd.local" | sudo tee -a /etc/hosts

# Expose the ArgoCD UI and API server via the Ingress
kubectl apply -f infrastructure/argocd/argocd-ingress.yaml

# Retrieve the initial admin password (auto-generated at install time)
argocd admin initial-password -n argocd
```

> **Important:** Change the initial admin password immediately after first
> login. Navigate to `User Info → Update Password` in the ArgoCD UI, or run:
> `argocd account update-password`

---

## 3. Initial Setup

Apply the project configuration resources in this order after installation.

```bash
# 1. Apply the RBAC ConfigMap (defines developer, pipeline, admin roles)
kubectl apply -f infrastructure/argocd/argocd-rbac-cm.yaml

# 2. Create the AppProject (fences apps to myapp-* namespaces and this repo)
kubectl apply -f infrastructure/argocd/myapp-project.yaml

# 3. Register the repository (only required for private repos)
argocd repo add https://github.com/<ORG>/<REPO> \
  --username <user> --password <token>

# 4. Create the Applications — dev first, then staging, then prod
kubectl apply -f gitops/apps/dev/myapp.yaml
kubectl apply -f gitops/apps/staging/myapp.yaml
kubectl apply -f gitops/apps/prod/myapp.yaml

# 5. Verify all three Applications are registered and syncing
argocd app list
```

---

## 4. GitOps Workflow (end-to-end)

```
Developer pushes code to main
        ↓
CI (GitHub Actions build.yml) runs tests + builds Docker image
        ↓
Image pushed to container registry with tag <commit-sha>
        ↓
CI (update-image-tag.yml) patches image.tag in gitops/apps/dev/myapp.yaml
        ↓
CI commits the tag change to main with [skip ci]
        ↓
ArgoCD polls Git every 3 minutes (or receives webhook)
        ↓
ArgoCD detects the new commit — diff shows image.tag changed
        ↓
ArgoCD syncs myapp-dev namespace — new pods roll out
        ↓
ArgoCD health check passes — app shows Synced + Healthy
```

### Image Tag Patch Command

CI runs the following after a successful build to update the dev Application:

```bash
IMAGE_TAG="${GITHUB_SHA::7}"

# yq is used instead of sed — YAML-aware, safe when indentation changes
yq e '(.spec.source.helm.parameters[] | select(.name == "image.tag")).value = env(IMAGE_TAG)' \
  -i gitops/apps/dev/myapp.yaml

git config user.name  "github-actions[bot]"
git config user.email "github-actions[bot]@users.noreply.github.com"
git add gitops/apps/dev/myapp.yaml
git commit -m "chore: update myapp-dev image tag to ${IMAGE_TAG} [skip ci]"
git push
```

**`[skip ci]` convention** — the `[skip ci]` token in the commit message
instructs GitHub Actions to skip the build workflow on the tag-update commit.
Without it, the build workflow would re-trigger, build a new image, write
another commit, and loop indefinitely.

**3-minute poll interval** — ArgoCD polls the Git repository every 3 minutes
by default. To reduce this to near-instant, configure a webhook in the
repository settings: `Settings → Webhooks → Add webhook`, pointing to
`https://argocd.local/api/webhook`.

**Only dev is patched automatically** — CI writes only to
`gitops/apps/dev/myapp.yaml`. Promoting to staging or prod requires a
separate manual or gated step (implemented in Topic 7,
`ci/github-actions/update-image-tag.yml`).

---

## 5. Application Reference

| Application | Target namespace | Value files | Sync mode | Expected duration |
|---|---|---|---|---|
| `myapp-dev` | `myapp-dev` | `values-k8s.yaml` + `values-dev.yaml` | Automated (`selfHeal` + `prune`) | ~2 min |
| `myapp-staging` | `myapp-staging` | `values-k8s.yaml` + `values-staging.yaml` | Automated (`selfHeal` + `prune`) | ~2 min |
| `myapp-prod` | `myapp-prod` | `values-k8s.yaml` + `values-prod.yaml` | **Manual** — `argocd app sync myapp-prod` | ~3 min |

**Notes:**

- `selfHeal: true` on dev and staging means ArgoCD recreates any resource
  deleted or modified outside of Git within the 3-minute poll interval.
- `prune: true` removes cluster resources when the corresponding manifest is
  deleted from the Helm chart.
- Prod has no `syncPolicy.automated` block — the block is entirely absent,
  not set to `false`. This ensures no code path can accidentally enable
  auto-sync for production.

---

## 6. RBAC Reference

| Role | SSO group | Permissions | ArgoCD UI actions |
|---|---|---|---|
| `developer` | `developers` | sync + action on `myapp-dev`; get on `myapp/*` | Can trigger dev sync; view all app status |
| `pipeline` | `ci-pipeline` | sync on `myapp-dev` + `myapp-staging`; get on `myapp/*` | Used by CI service account; cannot touch prod |
| `admin` | `platform-admins` | full wildcard on `myapp/*`; cluster + repo read | Can sync prod, manage all resources, view cluster |
| `readonly` (default) | all unmatched users | view-only on all applications | Can see status; cannot sync or modify anything |

**Changing SSO group names:**

The group names (`developers`, `ci-pipeline`, `platform-admins`) must match
the group claims emitted by your IdP (identity provider). To change them,
edit the `g, <group>, role:<role>` lines in
`infrastructure/argocd/argocd-rbac-cm.yaml` and re-apply:

```bash
kubectl apply -f infrastructure/argocd/argocd-rbac-cm.yaml
kubectl rollout restart deployment/argocd-server -n argocd
```

---

## 7. AppProject Reference

The `myapp` AppProject enforces the following security boundaries.

**Project name:** `myapp`

All Application manifests must set `spec.project: myapp`. ArgoCD rejects
applications whose `spec.project` does not match a registered AppProject.

**Source repository restriction (`spec.sourceRepos`):**

Only `https://github.com/<ORG>/<REPO>` is permitted as a chart source.
ArgoCD will refuse to sync any Application in this project that references
an external Helm repository or chart registry.

**Destination namespace restriction (`spec.destinations`):**

Only `myapp-dev`, `myapp-staging`, and `myapp-prod` namespaces on the local
cluster (`https://kubernetes.default.svc`) are permitted targets. Attempting
to deploy into `kube-system`, `argocd`, or any other namespace is rejected.

**Permitted resource kinds (`spec.namespaceResourceWhitelist`):**

| API group | Kind |
|---|---|
| `apps` | `Deployment`, `StatefulSet` |
| `autoscaling` | `HorizontalPodAutoscaler` |
| _(core)_ | `Service`, `ConfigMap`, `Secret`, `ServiceAccount` |
| `networking.k8s.io` | `Ingress` |
| `policy` | `PodDisruptionBudget` |
| `monitoring.coreos.com` | `ServiceMonitor` |

**Cluster-scoped resources (`spec.clusterResourceWhitelist: []`):**

No cluster-scoped resources may be created by Applications in this project.
`ClusterRole`, `ClusterRoleBinding`, `Namespace`, and `CustomResourceDefinition`
objects are blocked. Namespace creation is handled by ArgoCD's own service
account via `CreateNamespace=true` in each Application's `syncOptions`.

**Deletion protection (`metadata.finalizers`):**

The `resources-finalizer.argocd.argoproj.io` finalizer is set on the project.
Deleting the project while Applications still reference it will block — the
Applications must be deleted first.

---

## 8. Sync Operations

```bash
# Force-sync dev immediately (bypasses the 3-minute poll interval)
argocd app sync myapp-dev

# Manually approve and trigger a prod deployment (required for every prod deploy)
argocd app sync myapp-prod

# Check full sync and health status
argocd app get myapp-dev
argocd app get myapp-staging
argocd app get myapp-prod

# Roll back dev to the previous synced revision
argocd app rollback myapp-dev

# Preview what ArgoCD would change without applying it (dry run)
argocd app diff myapp-dev

# List all sync history revisions with revision IDs
argocd app history myapp-dev
```

---

## 9. Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| App stuck in `OutOfSync` | Helm chart renders non-deterministically (e.g. random annotations) | Add `--set` overrides to pin the value, or fix the template to be deterministic |
| App stuck in `Progressing` | Pod not becoming Ready | `kubectl describe pod -n myapp-dev`; check image pull errors or liveness probe failures |
| `ComparisonError` | ArgoCD cannot clone the repository | Check repo credentials: `argocd repo list`; re-register if expired |
| Prod sync triggers automatically | `syncPolicy.automated` block accidentally added to prod Application | Remove the entire `automated` block from `gitops/apps/prod/myapp.yaml` and re-apply |
| RBAC error in UI | User not in the correct SSO group | Check group bindings in `argocd-rbac-cm.yaml`; confirm IdP group claim name matches |
| `image.tag` not updating | `yq` command in CI patched the wrong field | Run `argocd app get myapp-dev -o yaml` and inspect `spec.source.helm.parameters` |
| ArgoCD UI unreachable at `argocd.local` | `/etc/hosts` entry missing or minikube IP changed after restart | Re-run: `echo "$(minikube ip)  argocd.local" \| sudo tee -a /etc/hosts` |
| `argocd app sync` returns permission denied | User is in `readonly` or `developer` role and lacks sync permission | Confirm SSO group membership and check group bindings in `argocd-rbac-cm.yaml` |

---

## 10. ArgoCD Commands Reference

| Command | Purpose |
|---|---|
| `argocd version` | Confirm argocd CLI version |
| `argocd login argocd.local` | Authenticate CLI against the local ArgoCD instance |
| `argocd admin initial-password -n argocd` | Retrieve the initial admin password after installation |
| `argocd repo add <url> --username <user> --password <token>` | Register a private repository with HTTPS credentials |
| `argocd repo list` | List all registered repositories and their connection status |
| `argocd app list` | List all Applications with sync and health status |
| `argocd app get myapp-dev` | Show detailed status for the dev Application |
| `argocd app get myapp-prod` | Confirm prod AutoSync is disabled |
| `argocd app sync myapp-dev` | Force-sync dev immediately |
| `argocd app sync myapp-prod` | Manually approve and trigger a prod deployment |
| `argocd app diff myapp-dev` | Preview what would change without syncing |
| `argocd app rollback myapp-dev` | Roll back dev to the previous revision |
| `argocd app history myapp-dev` | List sync history with revision IDs |
| `argocd app delete myapp-dev` | Delete the dev Application (note: does not delete namespace resources unless prune is enabled) |
| `kubectl apply -f infrastructure/argocd/argocd-rbac-cm.yaml` | Apply RBAC configuration changes |
| `kubectl apply -f infrastructure/argocd/myapp-project.yaml` | Create or update the AppProject |
