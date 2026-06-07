# docs

Project documentation.

## Files (created as each topic is completed)

| File | Topic |
|------|-------|
| `architecture.md` | Overall system design |
| `docker.md` | Docker build and Compose usage |
| `kubernetes.md` | kubectl workflow, manifest explanations |
| `openshift.md` | OpenShift-specific concepts and differences |
| `helm.md` | Helm install, upgrade, rollback commands |
| `terraform.md` | plan → apply → destroy workflow |
| `ansible.md` | Playbook usage, vault, idempotency |
| `gitops.md` | ArgoCD setup and GitOps loop explanation |
| `ci.md` | Pipeline setup for each tool |
| `monitoring.md` | Prometheus, Grafana, alert setup |

## runbooks/

Operational procedures:
- `rollback.md` — how to roll back a bad deployment
- `scaling.md` — how to manually scale and tune HPA
- `incident-response.md` — on-call runbook

## adr/

Architecture Decision Records — why decisions were made, not just what.
