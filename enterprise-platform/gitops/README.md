# gitops

ArgoCD manifests — the source of truth for cluster state.

## Structure

- `apps/dev|staging|prod/myapp.yaml` — ArgoCD Application per environment
- `appsets/myapp-appset.yaml` — ApplicationSet generating all environments

## GitOps loop

```
code push → CI builds image → CI commits new tag here → ArgoCD detects
change → ArgoCD syncs cluster → pods updated
```

No `kubectl apply` in CI. The cluster state is always derived from this
directory.

## Sync policy

All applications use `syncPolicy: automated` with:
- `selfHeal: true` — drift is corrected automatically
- `prune: true` — resources removed from Git are removed from the cluster
