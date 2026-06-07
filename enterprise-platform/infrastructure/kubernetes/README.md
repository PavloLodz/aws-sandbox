# infrastructure/kubernetes

Raw Kubernetes manifests — the learning foundation before Helm.

## Structure

- `base/` — environment-agnostic resource definitions
- `overlays/dev|staging|prod/` — Kustomize patches per environment

## Usage

```bash
# Deploy dev overlay
kubectl apply -k overlays/dev

# Verify
kubectl get pods -n myapp

# Roll back
kubectl rollout undo deployment/myapp -n myapp

# Tear down
kubectl delete -k overlays/dev
```

## Why these exist alongside Helm

These manifests are the reference implementation. When a Helm template
produces unexpected output, compare it against the raw manifest here.
Kustomize overlays also serve as a lighter alternative to Helm for
simple environment-specific patching.
