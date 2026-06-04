# ADR 001 — Develop on Kubernetes, test on OpenShift

## Status
Accepted

## Date
$(date +%Y-%m-%d)

## Context

The project targets both Kubernetes (AWS EKS) and OpenShift (ROSA) as
deployment platforms. Both need to be learned. Development requires fast
iteration cycles — context switching to a heavy platform slows learning.

CRC (OpenShift Local) requires 4 CPU cores and 9 GB RAM minimum, takes
5+ minutes to start, and is significantly slower to iterate on than minikube.

minikube starts in ~30 seconds, supports the full Kubernetes API, and runs
comfortably on a development machine alongside other tools.

## Decision

Use minikube as the primary development and learning platform for all topics
except those that are OpenShift-specific (Topic 4: Routes, SCC, BuildConfigs,
Tekton/OpenShift Pipelines).

Use CRC (OpenShift Local) only for:
- Topic 4 (OpenShift-specific resources)
- Final integration testing before production
- Learning OpenShift-specific tooling (oc CLI, OperatorHub, OpenShift console)

## Consequences

**Positive:**
- Faster feedback loop during development
- Lower resource usage on the development machine
- Mirrors how real enterprise teams work (dev on k8s, prod on OpenShift)
- All Kubernetes knowledge transfers directly to OpenShift

**Negative / risks:**
- OpenShift SCC behaviour not caught until Topic 4
- Must remember to test with values-openshift.yaml before final deployment

**Mitigations:**
- Dockerfile already uses non-root UID 1000 (compatible with restricted SCC)
- Helm chart has conditional Route/Ingress switching from day one
- values-openshift.yaml is maintained in parallel with values-k8s.yaml
