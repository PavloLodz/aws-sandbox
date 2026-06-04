# ADR 003 — ArgoCD for GitOps continuous delivery

## Status
Accepted

## Date
$(date +%Y-%m-%d)

## Context

The project needs a continuous delivery mechanism that keeps the cluster
state in sync with Git. Options considered:

- **ArgoCD**: declarative GitOps, Kubernetes-native, strong RBAC, UI
- **Flux**: lighter, more Kubernetes-native, less UI
- **Jenkins X**: opinionated full CI/CD platform
- **Manual kubectl apply in CI**: simple but not GitOps

## Decision

Use **ArgoCD** for GitOps continuous delivery.

CI pipelines (GitHub Actions, Tekton) build and push the image, then commit
the new image tag to `gitops/`. ArgoCD detects the commit and syncs the
cluster. No `kubectl apply` runs in CI.

## Consequences

**Positive:**
- Full GitOps model — cluster state is always derived from Git
- Self-healing: drift is corrected automatically
- ArgoCD UI gives instant visibility into sync status and diff
- Works identically on Kubernetes and OpenShift
- ApplicationSets generate per-environment Applications from one definition

**Negative / risks:**
- Additional component to install and maintain on the cluster
- Developers must understand the GitOps loop to debug delivery issues

**Mitigations:**
- GitOps loop is documented in `docs/gitops.md` and `gitops/README.md`
- ArgoCD is installed via Helm (reproducible, version-pinned)
- RBAC configured so developers can sync dev but not prod
