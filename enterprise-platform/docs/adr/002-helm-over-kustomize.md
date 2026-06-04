# ADR 002 — Helm as production packaging tool, Kustomize for learning

## Status
Accepted

## Date
$(date +%Y-%m-%d)

## Context

Kubernetes applications need a packaging and templating solution for
deploying across multiple environments with different configurations.

Two main options exist:
- **Helm**: full templating engine, package registry, release management
- **Kustomize**: overlay-based patching, built into kubectl, no templating

## Decision

Use **Helm** as the production packaging and deployment tool (Topic 3+).
Use **Kustomize** in `infrastructure/kubernetes/overlays/` as a learning
step (Topic 2) before Helm is introduced.

Kustomize overlays are kept permanently as a reference and escape hatch
for resources that don't fit neatly into the Helm chart.

## Consequences

**Positive:**
- Helm's templating handles the OpenShift/Kubernetes conditional logic cleanly
- Helm releases give rollback capability (`helm rollback`)
- ArgoCD has first-class Helm support
- Industry standard — transferable skill

**Negative / risks:**
- Helm templates are more complex than raw YAML
- Two packaging approaches in the same repo could cause confusion

**Mitigations:**
- Kustomize overlays are clearly scoped to `infrastructure/kubernetes/`
- Helm chart is the single source of truth for production deployments
- This ADR documents the boundary between the two
