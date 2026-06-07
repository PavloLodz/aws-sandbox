# infrastructure/openshift

OpenShift-specific resources that have no Kubernetes equivalent.

## Files (created during Topic 4)

| File | Purpose |
|------|---------|
| `route.yaml` | Replaces Ingress — exposes the app with TLS edge termination |
| `buildconfig.yaml` | S2I (Source-to-Image) build definition |
| `imagestream.yaml` | Tracks built images, triggers redeployment on push |
| `scc-serviceaccount.yaml` | Binds the app ServiceAccount to the restricted SCC |
| `pipeline-run.yaml` | Triggers the Tekton pipeline from OpenShift Pipelines |

## Key differences from Kubernetes

- Routes replace Ingress
- SCCs replace PodSecurityPolicies / Pod Security Standards
- OpenShift assigns UIDs dynamically — never set `runAsUser` in pod specs
- `anyuid` SCC must never be used in production

## Usage

```bash
# Login to CRC
oc login -u developer https://api.crc.testing:6443

# Apply OpenShift resources
oc apply -f infrastructure/openshift/

# Deploy via Helm with OpenShift values
helm install myapp infrastructure/helm/myapp-chart \
  -f infrastructure/helm/myapp-chart/values-openshift.yaml
```
