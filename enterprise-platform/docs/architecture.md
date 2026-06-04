# Architecture Overview

## System diagram

```
                    ┌─────────────────────────────────────────┐
                    │              Developer                   │
                    └──────────────┬──────────────────────────┘
                                   │ git push
                    ┌──────────────▼──────────────────────────┐
                    │           GitHub                         │
                    │   (source of truth for code)            │
                    └──┬───────────────────────────┬──────────┘
                       │ webhook                   │ webhook
          ┌────────────▼──────────┐   ┌────────────▼──────────┐
          │   GitHub Actions CI   │   │   ArgoCD watches       │
          │  test → build → push  │   │   gitops/ for changes  │
          └────────────┬──────────┘   └────────────┬──────────┘
                       │ image + tag commit          │ sync
          ┌────────────▼──────────┐   ┌────────────▼──────────┐
          │   ECR (image registry)│   │  Kubernetes / OpenShift│
          └───────────────────────┘   │  (EKS / ROSA / CRC)   │
                                      └────────────┬──────────┘
                                                   │ scrapes
                                      ┌────────────▼──────────┐
                                      │  Prometheus + Grafana  │
                                      └───────────────────────┘
```

## Components

| Component | Tool | Location |
|-----------|------|----------|
| Application | Spring Boot 3.2 | `application/` |
| Containerisation | Docker + Compose | `application/Dockerfile` |
| Kubernetes manifests | kubectl + Kustomize | `infrastructure/kubernetes/` |
| OpenShift resources | oc CLI | `infrastructure/openshift/` |
| Package management | Helm | `infrastructure/helm/` |
| Cloud infra | Terraform | `infrastructure/terraform/` |
| Node config | Ansible | `infrastructure/ansible/` |
| GitOps delivery | ArgoCD | `gitops/` |
| CI pipelines | GitHub Actions / Tekton | `ci/` |
| Observability | Prometheus + Grafana | `monitoring/` |
| Image registry | AWS ECR | provisioned by Terraform |
| Database | AWS RDS PostgreSQL | provisioned by Terraform |

## Environments

| Environment | Cluster | Database | Image tag |
|-------------|---------|----------|-----------|
| dev | minikube (local) | Docker Compose PostgreSQL | commit SHA |
| staging | EKS | RDS (dev tier) | commit SHA |
| prod | EKS / ROSA | RDS (prod tier) | semver tag |

## Key design decisions

See `docs/adr/` for the full rationale behind each decision:
- ADR 001 — Kubernetes-first development, OpenShift for final testing
- ADR 002 — Helm for production packaging, Kustomize for learning
- ADR 003 — ArgoCD for GitOps continuous delivery
