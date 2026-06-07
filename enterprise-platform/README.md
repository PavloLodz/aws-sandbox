# enterprise-platform

Full-stack learning project covering the complete DevOps and cloud-native
toolchain for a Spring Boot application.

## Structure

| Directory | Purpose |
|-----------|---------|
| `application/` | Spring Boot REST API (aws-attempt) |
| `infrastructure/kubernetes/` | Raw Kubernetes manifests + Kustomize overlays |
| `infrastructure/openshift/` | OpenShift-specific resources (Routes, SCC, BuildConfigs) |
| `infrastructure/helm/` | Helm chart for Kubernetes and OpenShift deployment |
| `infrastructure/terraform/` | AWS infrastructure provisioning (EKS, RDS, ECR) |
| `infrastructure/ansible/` | Node configuration management and deployment playbooks |
| `gitops/` | ArgoCD Application and ApplicationSet manifests |
| `monitoring/` | Prometheus rules, Grafana dashboards, Alertmanager config |
| `ci/` | CI pipelines: GitHub Actions, Jenkins, Tekton |
| `docs/` | Architecture docs, runbooks, ADRs |
| `scripts/` | Utility scripts |

## Quick start

```bash
make help       # list all available commands
make up         # start app locally with Docker Compose
make k8s-dev    # deploy to minikube
make helm-lint  # validate Helm chart
make test       # run all application tests
```

