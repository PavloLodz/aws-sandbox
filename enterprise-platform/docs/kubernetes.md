# Kubernetes — Topic 2

How to deploy `myapp` on minikube using raw manifests and Kustomize overlays.

---

## Prerequisites

Confirm all of the following before applying any manifests:

```bash
# Start minikube with enough resources for all pods
minikube start --cpus=4 --memory=6144

# Enable the nginx ingress controller (required for myapp.local routing)
minikube addons enable ingress

# Enable metrics-server (required for HPA CPU metrics)
minikube addons enable metrics-server

# Build the image and load it into minikube's internal registry
# (Deployment uses imagePullPolicy: Never — the image must exist locally)
cd application
docker build -t myapp:local .
minikube image load myapp:local

# Verify the image is available inside minikube
minikube image ls | grep myapp

# Add myapp.local to /etc/hosts (run once — sudo required)
echo "$(minikube ip) myapp.local" | sudo tee -a /etc/hosts
```

---

## Deploying

All three overlays reference the same base manifests — only replicas, resource
limits, and PDB settings differ between environments.

```bash
# Dev — 1 replica, reduced resources (use for local iteration on minikube)
kubectl apply -k infrastructure/kubernetes/overlays/dev

# Staging — 2 replicas, standard resources
kubectl apply -k infrastructure/kubernetes/overlays/staging

# Prod — 3 replicas, higher resources, stricter PDB (minAvailable: 2)
kubectl apply -k infrastructure/kubernetes/overlays/prod
```

To apply the base directly without any overlay:

```bash
kubectl apply -k infrastructure/kubernetes/base
```

Watch pods come up after applying:

```bash
kubectl get pods -n myapp -w
# postgres-0 must reach 1/1 Running before myapp pods become Ready
# myapp pods become Ready ~60s after postgres is up (readiness initialDelay)
```

---

## Verifying

After pods are Running, confirm all resources are healthy:

```bash
# Pod status — all must show READY 1/1 and STATUS Running
kubectl get pods -n myapp

# Service — confirm ClusterIP is assigned
kubectl get svc -n myapp

# Ingress — confirm ADDRESS is populated (may take 60s for nginx to assign)
kubectl get ingress myapp -n myapp

# HPA — confirm TARGETS shows CPU% / 70% (not <unknown>)
kubectl get hpa myapp -n myapp

# PDB — confirm ALLOWED DISRUPTIONS is > 0
kubectl get pdb myapp -n myapp

# End-to-end HTTP check through the Ingress
curl http://myapp.local/api/items
# expected: [] or a JSON array — HTTP 200

# Swagger UI (redirect to swagger-ui/index.html)
curl -s -o /dev/null -w "%{http_code}" http://myapp.local/swagger-ui.html
# expected: 302
```

---

## Logs and Events

```bash
# Stream app logs from all myapp pods
kubectl logs -l app=myapp -n myapp --follow

# Logs from a single pod (replace <pod-name> with actual name)
kubectl logs <pod-name> -n myapp

# Describe a pod — shows resource usage, probe status, and recent events
kubectl describe pod -l app=myapp -n myapp

# All namespace events sorted by time — most useful when pods won't start
kubectl get events -n myapp --sort-by='.lastTimestamp'

# Postgres logs
kubectl logs -l app=postgres -n myapp --follow
```

---

## Rollout and Rollback

```bash
# Trigger a rolling update with a new image tag
kubectl set image deployment/myapp myapp=myapp:0.0.2 -n myapp

# Watch the rollout progress (maxUnavailable: 0 means zero downtime)
kubectl rollout status deployment/myapp -n myapp

# Roll back to the previous version
kubectl rollout undo deployment/myapp -n myapp

# View rollout history
kubectl rollout history deployment/myapp -n myapp

# Scale manually (overrides HPA temporarily)
kubectl scale deployment/myapp --replicas=3 -n myapp
```

---

## Resilience Verification

The Deployment is configured with `maxUnavailable: 0` and `maxSurge: 1`.
Deleting a pod must not cause downtime — a replacement starts immediately.

```bash
# Delete a pod — watch the replacement appear (use -w to stream)
kubectl delete pod -l app=myapp -n myapp
kubectl get pods -n myapp -w
# A new pod must appear and reach Running before the deleted pod terminates

# Confirm the PDB is protecting pods during voluntary disruptions
kubectl get pdb myapp -n myapp
# ALLOWED DISRUPTIONS column must be > 0 (otherwise no voluntary evictions allowed)
```

---

## Teardown

```bash
# Remove overlay resources (leaves namespace and PVCs intact)
kubectl delete -k infrastructure/kubernetes/overlays/dev

# Remove all resources including the namespace (fastest full teardown)
kubectl delete namespace myapp

# Remove PersistentVolumeClaims left behind by the StatefulSet
kubectl delete pvc -l app=postgres -n myapp

# Stop minikube (preserves cluster state — fast to restart)
minikube stop

# Delete the cluster entirely (frees all CPU and memory)
minikube delete
```

---

## Resource Types Reference

| Resource | Purpose in this project |
|---|---|
| `Namespace` | Isolates all `myapp` resources; `kubectl delete namespace myapp` is the single-command teardown |
| `ServiceAccount` | Pod identity with `automountServiceAccountToken: false` — no API access mounted by default |
| `ConfigMap` | Non-sensitive config (`SPRING_PROFILES_ACTIVE`, `LOGGING_LEVEL_ROOT`, `SERVER_PORT`) injected as env vars |
| `Secret` | Base64-encoded DB credentials; consumed via `envFrom` in the Deployment — never inline |
| `Deployment` | Manages app replicas with rolling update (`maxUnavailable: 0`), probes, and grace period |
| `StatefulSet` | Manages PostgreSQL with a stable DNS name and dedicated PersistentVolume — local dev only |
| `Service` | Stable ClusterIP DNS name (`myapp.myapp.svc.cluster.local`) and load balancer across pods |
| `Ingress` | Routes external HTTP traffic from `myapp.local` to the Service via the nginx controller |
| `HorizontalPodAutoscaler` | Scales Deployment replicas 2–5 based on CPU utilisation (target 70%) |
| `PodDisruptionBudget` | Prevents voluntary disruptions from taking down more pods than the minimum available allows |
