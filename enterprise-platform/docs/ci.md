# CI Pipelines — Topic 7

Operational runbook for the three CI pipeline implementations in the `ci/`
directory. Each pipeline tests the application, builds a container image
tagged with the commit SHA, pushes it to the registry, and updates the GitOps
manifest so ArgoCD deploys the new image to dev automatically — with no manual
intervention between `git push` and a running pod.

---

## Table of Contents

1. [Overview](#1-overview)
2. [Prerequisites](#2-prerequisites)
3. [Shared Rules](#3-shared-rules)
4. [GitHub Actions](#4-github-actions)
5. [Jenkins](#5-jenkins)
6. [Tekton](#6-tekton)
7. [Troubleshooting](#7-troubleshooting)
8. [CI Commands Reference](#8-ci-commands-reference)

---

## 1. Overview

A developer pushes a commit to `main`. Within minutes:

1. The CI pipeline runs `mvn verify` — unit tests and Testcontainers integration tests.
2. On a green test run, a Docker image is built and pushed to the container registry, tagged with the 7-char commit SHA.
3. The `image.tag` value in `gitops/apps/dev/myapp.yaml` is patched to the new SHA and committed.
4. ArgoCD detects the GitOps commit and syncs the dev cluster — the new image is running.

No manual `kubectl apply` or image tagging step is ever required.

| Platform | Primary use case | Implemented files |
|---|---|---|
| GitHub Actions | Cloud-native CI — runs on every push to `main` and every PR | `ci/github-actions/build.yml`, `ci/github-actions/release.yml`, `ci/github-actions/update-image-tag.yml` |
| Jenkins | Self-hosted CI — equivalent pipeline for teams running an on-premise Jenkins instance | `ci/jenkins/Jenkinsfile.build` |
| Tekton | Kubernetes-native CI — prepared for Topic 10; runs entirely inside the cluster | `ci/tekton/tasks/`, `ci/tekton/pipeline.yaml`, `ci/tekton/triggers/github-trigger.yaml` |

---

## 2. Prerequisites

### GitHub Actions

- Repository hosted on GitHub.
- The four secrets listed in [Section 4.1](#41-required-secrets) configured under `Settings → Secrets and variables → Actions`.
- No additional tooling — GitHub-hosted runners provide Java, Maven, and Docker out of the box.

### Jenkins

- Running Jenkins instance (≥ 2.440) with Docker available on the build agent.
- The host Docker socket (`/var/run/docker.sock`) accessible to the agent — required for Testcontainers integration tests.
- Four plugins installed (see [Section 5.1](#51-required-plugins)).
- Two credentials configured (see [Section 5.2](#52-credential-configuration)).

### Tekton

| Tool | Minimum version | Check command |
|---|---|---|
| kubectl | ≥ 1.27 | `kubectl version --client` |
| Tekton Pipelines | ≥ 0.59 | `kubectl get deployment -n tekton-pipelines` |
| Tekton Triggers | ≥ 0.26 | `kubectl get deployment -n tekton-pipelines tekton-triggers-controller` |
| Tekton Interceptors | ≥ 0.26 | `kubectl get deployment -n tekton-pipelines tekton-triggers-core-interceptors` |
| tkn CLI | ≥ 0.36 | `tkn version` |

---

## 3. Shared Rules

### 3.1 R7.8 — Never build the image before tests pass

All three platforms enforce a strict gate: the Docker image build step is
unreachable unless `mvn verify` exits 0.

| Platform | Enforcement mechanism |
|---|---|
| GitHub Actions | Single linear job — `Run tests (mvn verify)` step precedes `Build and push Docker image`; a failing step terminates the job, blocking all subsequent steps |
| Jenkins | Declarative pipeline — stage failure halts the run by default; `Build Image` stage is never reached if `Test` stage fails |
| Tekton | `runAfter: [ test ]` on the `build-image` Task — Tekton stops the Pipeline on any TaskRun failure; no downstream Task starts until every upstream Task succeeds |

### 3.2 R7.9 — Never store secrets in pipeline files

Plaintext credentials in any pipeline file (`build.yml`, `Jenkinsfile.build`,
any Tekton YAML) constitute an **immediate PR rejection**. Every secret must
be stored in the appropriate secret management system for the platform.

| Platform | Secret storage | Navigation path |
|---|---|---|
| GitHub Actions | GitHub Encrypted Secrets | `Settings → Secrets and variables → Actions → New repository secret` |
| Jenkins | Jenkins Credentials Store | `Manage Jenkins → Credentials → System → Global credentials → Add Credentials` |
| Tekton | Kubernetes Secrets | `kubectl create secret …` (examples in [Section 6.3](#63-required-secrets-and-pvcs)) |

### 3.3 `[skip ci]` commit convention

The `update-image-tag.yml` workflow commits a patched `gitops/apps/dev/myapp.yaml`
back to `main`. Without a guard, this commit would trigger `build.yml` again,
causing an infinite loop:

```
push to main → build.yml → update-image-tag.yml → commit to main → build.yml → …
```

The commit message `chore: update myapp-dev image tag to <sha> [skip ci]`
breaks the loop. GitHub Actions skips workflow runs whose triggering commit
message contains `[skip ci]`. The same convention applies to the equivalent
Tekton `update-gitops` Task commit.

---

## 4. GitHub Actions

### 4.1 Required Secrets

Navigate to: `Settings → Secrets and variables → Actions → New repository secret`

| Secret name | Type | Description |
|---|---|---|
| `REGISTRY_URL` | Secret text | Container registry hostname (e.g. `ghcr.io/org`) |
| `REGISTRY_USERNAME` | Secret text | Registry login username |
| `REGISTRY_PASSWORD` | Secret text | Registry login password or access token |
| `GITOPS_PAT` | Secret text | Personal Access Token with `repo` scope — required to push to branch-protected `main` (the default `GITHUB_TOKEN` is blocked on protected branches) |

### 4.2 `build.yml` — Test, Build & Push

**Trigger:** `push` to `main`; all `pull_request` events (all branches).

**Stage sequence:**

```
checkout → setup-java (Maven cache) → mvn verify → compute tag →
setup-buildx → registry login → docker build+push → build summary
```

| Step | Detail |
|---|---|
| Setup Java | `actions/setup-java@v4` with `cache: 'maven'` — caches `~/.m2/repository` keyed on the `pom.xml` hash; saves 60–90 s on a warm cache |
| Run tests | `mvn verify --batch-mode` in the `application/` directory — runs compile, surefire unit tests, package, and failsafe integration tests via Testcontainers |
| Compute tag | `GITHUB_SHA::7` — 7-char short SHA, traceable to source, safe in Kubernetes labels |
| Build & push | `push: ${{ github.event_name != 'pull_request' }}` — PRs build the image (validates the Dockerfile) but do **not** push; only `main` pushes result in a registry image |
| Build summary | Written to `$GITHUB_STEP_SUMMARY` — visible in the GitHub Actions run page under the "Summary" tab |

### 4.3 `update-image-tag.yml` — GitOps Patch Workflow

**Trigger:** `workflow_run` on `Build` completion, `branches: [main]` only.

**Why `workflow_run` instead of `push`:** A `push` trigger would start
`update-image-tag.yml` in parallel with `build.yml`. If `build.yml` fails
(e.g. a test regression), `update-image-tag.yml` would still run and commit a
tag pointing to an image that was never pushed. `workflow_run` fires only
after `Build` has completed, and the `if: conclusion == 'success'` guard
ensures the gitops commit only happens after a fully green build.

**Why `GITOPS_PAT` instead of `GITHUB_TOKEN`:** The default `GITHUB_TOKEN`
is blocked from pushing to branches that have branch protection rules enabled.
A Personal Access Token with `repo` scope must be stored as the `GITOPS_PAT`
secret.

**Pre-commit validation:** Before touching `gitops/apps/dev/myapp.yaml`, the
workflow runs:

```bash
helm lint infrastructure/helm/myapp-chart \
  -f infrastructure/helm/myapp-chart/values-k8s.yaml \
  -f infrastructure/helm/myapp-chart/values-dev.yaml

helm template myapp infrastructure/helm/myapp-chart \
  -f infrastructure/helm/myapp-chart/values-k8s.yaml \
  -f infrastructure/helm/myapp-chart/values-dev.yaml \
  --set image.tag=<sha> > /dev/null
```

If the chart has a syntax or rendering error, the workflow fails before any
Git write occurs — ArgoCD is never pointed at a broken chart state.

**Idempotency:** `git diff --staged --quiet && exit 0` — if the SHA is already
committed (e.g. on a manual re-run), the workflow exits cleanly without
creating an empty commit.

### 4.4 `release.yml` — Semver Retag

**Trigger:** `push` of tags matching `v*.*.*`.

**Retag, not rebuild:** `release.yml` pulls the SHA image that was built and
tested by `build.yml`, then applies new tags (`v1.2.3` and `latest`). It does
not rebuild from source. This guarantees the semver-tagged image has the
**exact same layer digest** as the image that passed all tests — rebuilding
from source produces a different hash even for identical inputs.

**When to cut a release tag:**

```bash
# Tag the commit that was built on main (the SHA image must already exist)
git tag v1.2.3
git push origin v1.2.3
```

Only tag commits that were built on `main` — PR builds skip the push step, so
there is no SHA image in the registry for those commits.

**`latest` tag:** The `latest` tag is pushed for `docker pull myapp`
convenience only. It must **never** be referenced in any Kubernetes manifest
or Helm values file — every deployed image must be traceable to an exact commit
SHA.

---

## 5. Jenkins

### 5.1 Required Plugins

Navigate to: `Manage Jenkins → Plugins → Available plugins`

| Plugin name | Plugin ID | Purpose |
|---|---|---|
| Pipeline | `workflow-aggregator` | Declarative pipeline syntax |
| Docker Pipeline | `docker-workflow` | `docker.build` / `docker.withRegistry` steps |
| Credentials Binding | `credentials-binding` | `withCredentials` block for secret injection |
| JUnit | `junit` | Test result archiving and trend graphs |

### 5.2 Credential Configuration

Navigate to: `Manage Jenkins → Credentials → System → Global credentials → Add Credentials`

| Credential ID | Kind | Value |
|---|---|---|
| `REGISTRY_URL` | Secret text | Container registry hostname (e.g. `ghcr.io/org`) |
| `REGISTRY_CREDS` | Username with password | Registry username and password/token |

Credential IDs are case-sensitive and must match exactly as shown — the
Jenkinsfile references them by these exact IDs.

### 5.3 Creating the Jenkins Job

1. `New Item` → enter a job name (e.g. `myapp-build`) → select **Pipeline** → click **OK**
2. Scroll to the **Pipeline** section → set **Definition** to `Pipeline script from SCM`
3. Set **SCM** to `Git`
4. Enter the **Repository URL**
5. Set **Branch Specifier** to `*/main`
6. Set **Script Path** to `ci/jenkins/Jenkinsfile.build`
7. Click **Save**

The pipeline will run automatically on the next push to `main` if a webhook is
configured, or can be triggered manually via **Build Now**.

### 5.4 Pipeline Stages

**Test**

Runs inside the `maven:3.9-eclipse-temurin-21` Docker image (no Java setup
required on the agent). The host Docker socket is mounted into the container
for Testcontainers support:

```groovy
args '-v /var/run/docker.sock:/var/run/docker.sock'
```

`-Dmaven.repo.local=.m2` writes the Maven cache inside the Jenkins workspace
directory so it can be preserved by Jenkins workspace caching between builds.

After the test run (pass or fail), JUnit results are archived:

```groovy
junit 'application/target/surefire-reports/*.xml'          // unit tests
junit allowEmptyResults: true,
      testResults: 'application/target/failsafe-reports/*.xml'  // integration tests
```

`allowEmptyResults: true` on the failsafe step prevents a build failure when
no integration tests have run — the glob would otherwise match nothing and
cause a step error.

**Build Image** (R7.8)

Only reached when the Test stage exits 0. Declarative pipeline halts the run
on stage failure by default — no explicit condition is needed. The full image
reference is assembled from the `REGISTRY_URL` credential and the 7-char
`GIT_COMMIT` SHA:

```groovy
env.FULL_IMAGE = "${REGISTRY}/myapp:${IMAGE_TAG}"
docker.build(env.FULL_IMAGE, '-f application/Dockerfile application')
```

**Push Image**

Gated to the `main` branch only — mirrors `build.yml`'s PR-no-push behaviour:

```groovy
when { branch 'main' }
```

All push credentials flow through `docker.withRegistry` using the
`REGISTRY_CREDS` credential — no plaintext values appear in the file (R7.9).

---

## 6. Tekton

### 6.1 Installation

Install the three Tekton controllers in order. Replace `<version>` with the
desired release tag (e.g. `v0.59.0`).

```bash
# 1. Tekton Pipelines
kubectl apply -f \
  https://storage.googleapis.com/tekton-releases/pipeline/previous/<version>/release.yaml

# 2. Tekton Triggers
kubectl apply -f \
  https://storage.googleapis.com/tekton-releases/triggers/previous/<version>/release.yaml

# 3. Tekton Interceptors (required for github and cel interceptors)
kubectl apply -f \
  https://storage.googleapis.com/tekton-releases/triggers/previous/<version>/interceptors.yaml

# Wait for all controllers to be ready
kubectl wait --for=condition=Available deployment \
  --all -n tekton-pipelines --timeout=120s
```

### 6.2 Applying Tasks and Pipeline

Apply resources in order — Tasks must exist before the Pipeline references them,
and the Pipeline must exist before the TriggerTemplate references it.

```bash
# 1. Apply all five Task resources
kubectl apply -f ci/tekton/tasks/

# 2. Apply the Pipeline
kubectl apply -f ci/tekton/pipeline.yaml

# 3. Apply the Trigger resources and maven-cache-pvc
kubectl apply -f ci/tekton/triggers/github-trigger.yaml

# Verify
kubectl get tasks
kubectl get pipeline myapp-pipeline
kubectl get eventlistener github-push-listener
```

### 6.3 Required Secrets and PVCs

All secrets must be created **before** the first PipelineRun. No secret values
appear in any file under `ci/` (R7.9).

**`registry-dockerconfig`** — registry push credentials for `build-image-task` and `push-image-task`:

```bash
kubectl create secret docker-registry registry-dockerconfig \
  --docker-server=<REGISTRY_HOSTNAME> \
  --docker-username=<USERNAME> \
  --docker-password=<PASSWORD_OR_TOKEN>
```

**`git-credentials`** — SSH key for git push in `update-gitops-task`:

```bash
# SSH key option
kubectl create secret generic git-credentials \
  --from-file=ssh-privatekey=~/.ssh/id_rsa

# HTTPS .netrc option
kubectl create secret generic git-credentials \
  --from-literal=.netrc="machine github.com login <user> password <token>"
```

**`github-webhook-secret`** — HMAC token for EventListener signature validation:

```bash
# Generate a random token and store it — use the same value in the GitHub webhook config
kubectl create secret generic github-webhook-secret \
  --from-literal=token=<RANDOM_HMAC_TOKEN>
```

**`maven-cache-pvc`** — included in `ci/tekton/triggers/github-trigger.yaml`
and applied in Step 6.2. Verify it was created:

```bash
kubectl get pvc maven-cache-pvc
```

### 6.4 Configuring the GitHub Webhook

1. Obtain the EventListener service external IP:

```bash
kubectl get svc -l eventlistener=github-push-listener
# Note the EXTERNAL-IP of the el-github-push-listener service
```

2. In the GitHub repository: `Settings → Webhooks → Add webhook`

| Field | Value |
|---|---|
| Payload URL | `http://<EXTERNAL-IP>:8080` |
| Content type | `application/json` |
| Secret | The value used when creating `github-webhook-secret` |
| Which events | **Just the push event** |

3. Click **Add webhook**. GitHub will send a ping event — a green tick confirms
   the EventListener is reachable and the HMAC signature validated successfully.

### 6.5 Monitoring PipelineRuns

```bash
# List all PipelineRuns with status
kubectl get pipelineruns

# Same output with tkn CLI (more readable)
tkn pipelinerun list

# Stream logs for a specific run (all TaskRuns in order)
tkn pipelinerun logs <pipelinerun-name> -f

# Describe a PipelineRun (shows TaskRun status, params, workspaces)
tkn pipelinerun describe <pipelinerun-name>
```

---

## 7. Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `Cannot connect to Docker daemon` during tests | Testcontainers requires access to a Docker daemon | **GitHub Actions:** Docker is pre-installed on `ubuntu-latest` — no action needed. **Jenkins:** Verify `args '-v /var/run/docker.sock:/var/run/docker.sock'` is present in the agent block. **Tekton:** Verify the `docker-sock` hostPath volume is defined in `test-task.yaml` and mounted at `/var/run/docker.sock` |
| `update-image-tag.yml` push rejected with `403` | `GITOPS_PAT` secret is missing or the PAT lacks `repo` scope | Create a GitHub PAT with `repo` scope; store it as the `GITOPS_PAT` repository secret via `Settings → Secrets and variables → Actions` |
| Release retag fails: `manifest unknown` | The SHA image was never pushed — PR builds skip the push step | Only push `v*.*.*` tags for commits that were built from `main`; verify the SHA image exists in the registry before tagging |
| Tekton `maven-test` TaskRun fails: Docker daemon not found | The `docker-sock` hostPath volume is absent or misconfigured | Confirm `test-task.yaml` declares the `docker-sock` `hostPath` volume at spec level and mounts it at `/var/run/docker.sock` in the `verify` step |
| ArgoCD not syncing after `gitops/apps/dev/myapp.yaml` is updated | ArgoCD polls Git every 3 minutes by default | Wait up to 3 minutes, or trigger an immediate sync: `argocd app sync myapp-dev` — alternatively, configure a Git webhook to ArgoCD at `https://argocd.local/api/webhook` |
| `helm lint` fails in `update-image-tag.yml` | A chart syntax error was introduced since the last passing lint | Run `helm lint infrastructure/helm/myapp-chart -f infrastructure/helm/myapp-chart/values-k8s.yaml -f infrastructure/helm/myapp-chart/values-dev.yaml` locally, fix the error, and push |
| Tekton EventListener not triggering on push | Webhook HMAC signature mismatch | Verify the value in the `github-webhook-secret` Kubernetes Secret exactly matches the **Secret** field set in the GitHub webhook configuration; recreate the secret if needed |

---

## 8. CI Commands Reference

### Day-to-day commands

```bash
# ── Helm (local validation) ────────────────────────────────────────────────
# Lint the chart against both value files
helm lint infrastructure/helm/myapp-chart \
  -f infrastructure/helm/myapp-chart/values-k8s.yaml \
  -f infrastructure/helm/myapp-chart/values-dev.yaml

# Render templates to verify output (dry run)
helm template myapp infrastructure/helm/myapp-chart \
  -f infrastructure/helm/myapp-chart/values-k8s.yaml \
  -f infrastructure/helm/myapp-chart/values-dev.yaml \
  --set image.tag=abc1234

# ── Tekton PipelineRuns ────────────────────────────────────────────────────
# List all PipelineRuns with status
tkn pipelinerun list

# Stream logs for the most recent PipelineRun
tkn pipelinerun logs --last -f

# Stream logs for a named PipelineRun
tkn pipelinerun logs myapp-pipeline-run-x8k2p -f

# List PipelineRuns with kubectl (includes timestamps)
kubectl get pipelineruns

# ── Tekton EventListener ───────────────────────────────────────────────────
# Get EventListener service external IP (for webhook URL)
kubectl get svc -l eventlistener=github-push-listener
```

### Manually trigger a PipelineRun (without a webhook)

Useful for local testing or re-running a specific commit:

```bash
# Option A — tkn CLI (interactive, prompts for params)
tkn pipeline start myapp-pipeline \
  --param git-url=https://github.com/<ORG>/<REPO> \
  --param git-revision=main \
  --param image=<REGISTRY>/myapp \
  --param image-tag=$(git rev-parse --short HEAD) \
  --workspace name=source,volumeClaimTemplateFile=ci/tekton/workspace-source.yaml \
  --workspace name=maven-cache,claimName=maven-cache-pvc \
  --workspace name=dockerconfig,secret=registry-dockerconfig \
  --workspace name=git-credentials,secret=git-credentials \
  --showlog

# Option B — apply a PipelineRun manifest directly
kubectl create -f - <<EOF
apiVersion: tekton.dev/v1
kind: PipelineRun
metadata:
  generateName: myapp-manual-run-
spec:
  pipelineRef:
    name: myapp-pipeline
  params:
    - name: git-url
      value: https://github.com/<ORG>/<REPO>
    - name: git-revision
      value: main
    - name: image
      value: <REGISTRY>/myapp
    - name: image-tag
      value: $(git rev-parse --short HEAD)
  workspaces:
    - name: source
      volumeClaimTemplate:
        spec:
          accessModes: [ ReadWriteOnce ]
          resources:
            requests:
              storage: 1Gi
    - name: maven-cache
      persistentVolumeClaim:
        claimName: maven-cache-pvc
    - name: dockerconfig
      secret:
        secretName: registry-dockerconfig
    - name: git-credentials
      secret:
        secretName: git-credentials
EOF
```

---

## Acceptance Criteria

- [ ] Pushing to `main` triggers `build.yml`; a green run pushes the image tagged with the 7-char commit SHA to the registry
- [ ] A failing test in `mvn verify` causes `build.yml` to fail before `docker build` — no image is built or pushed
- [ ] On a PR, `build.yml` builds the Docker image but does not push it to the registry
- [ ] `update-image-tag.yml` triggers automatically after a successful `build.yml` on `main`; the new SHA is committed to `gitops/apps/dev/myapp.yaml` and ArgoCD syncs within 3 minutes
- [ ] Pushing a `v*.*.*` tag triggers `release.yml`; the registry contains both a semver-tagged and `latest`-tagged image with the same digest as the SHA image
- [ ] No plaintext credentials appear in any pipeline file (`build.yml`, `release.yml`, `update-image-tag.yml`, `Jenkinsfile.build`, any Tekton YAML)
- [ ] `Jenkinsfile.build` loads in Jenkins without syntax errors; all three stages are visible in Blue Ocean
- [ ] `kubectl apply -f ci/tekton/tasks/` installs all five Tasks without errors
- [ ] `kubectl apply -f ci/tekton/pipeline.yaml` creates the Pipeline with the five-task sequence in the correct order
- [ ] A GitHub webhook push to the EventListener creates a `PipelineRun`; `tkn pipelinerun logs` shows all five Tasks completing in order
- [ ] `docs/ci.md` covers all three platforms with secret setup, stage descriptions, and troubleshooting

---

## Key Design Decisions

| Decision | Rationale |
|---|---|
| Linear steps in one job (GitHub Actions) | R7.8 enforced by sequential execution — failing `mvn verify` terminates the job before `docker build` runs |
| `push: github.event_name != 'pull_request'` | Validates the Dockerfile on PRs without pushing unreviewed images to the shared registry |
| `workflow_run` + `conclusion == 'success'` | Prevents gitops commits after a failed build; a `push` trigger would race with the build job and could commit a tag for an image that was never pushed |
| `yq` for YAML patching (not `sed`) | YAML-aware — safe when indentation, key ordering, or quoting changes between chart versions; `sed` would silently produce malformed YAML |
| `[skip ci]` in gitops commit message | Breaks the infinite loop between `build.yml` and `update-image-tag.yml` — GitHub Actions skips runs whose triggering commit message contains `[skip ci]` |
| `GITOPS_PAT` instead of `GITHUB_TOKEN` | The default `GITHUB_TOKEN` cannot push to branches protected by branch protection rules |
| Retag for release (not rebuild) | Guarantees the semver image has the same layer digest as the SHA image that passed all tests — rebuilding produces a different hash even for identical source inputs |
| Kaniko over Docker-in-Docker for Tekton | No `privileged: true` required — safe on OpenShift SCCs and Kubernetes PodSecurityAdmission restricted policies |
| `volumeClaimTemplate` per PipelineRun for source | Fresh clone per run — prevents cross-run source contamination when concurrent builds share the same namespace |
| Shared `maven-cache-pvc` PVC | Avoids re-downloading ~200 MB of Maven dependencies on every run — the PVC is long-lived and shared across all PipelineRuns |
| `git diff --staged --quiet && exit 0` | Idempotent re-runs — no empty commits are created when the tag is already up to date in the GitOps manifest |
| `disableConcurrentBuilds()` in Jenkins | Prevents resource contention between concurrent Testcontainers-using builds sharing the same Docker socket and network port range on a single agent |
