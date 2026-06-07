# ci

CI pipeline definitions for three platforms.

## Platforms

| Directory | Platform | When to use |
|-----------|----------|-------------|
| `github-actions/` | GitHub Actions | Primary CI — runs on every push and PR |
| `jenkins/` | Jenkins | Enterprise alternative, integrates with OpenShift |
| `tekton/` | Tekton | Kubernetes-native, used inside OpenShift Pipelines |
| `shared/` | — | Dockerfile and .dockerignore shared across all pipelines |

## Pipeline stages (all platforms)

```
clone → test (mvn verify) → build image → push to ECR → update gitops tag
```

The `update gitops tag` step commits the new image digest to `gitops/apps/`
which triggers ArgoCD to sync the cluster automatically.

## Secrets

Never store secrets in pipeline definitions. Use:
- GitHub Actions: repository secrets
- Jenkins: credentials binding plugin
- Tekton: Kubernetes Secrets referenced in the Pipeline
