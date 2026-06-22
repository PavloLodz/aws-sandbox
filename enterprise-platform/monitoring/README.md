# monitoring

Prometheus, Grafana and Alertmanager configuration.

## Structure

- `prometheus/rules/` — alerting rules (HighErrorRate, PodNotReady, HighMemoryUsage)
- `grafana/dashboards/` — dashboard JSON files (provisioned automatically)
- `alertmanager/config.yaml` — alert routing to Slack

## Dashboards

| File | Shows |
|------|-------|
| `application.json` | Request rate, error rate, p95 latency, JVM heap, DB connections |
| `business.json` | Items created/deleted per minute, fetch duration histogram |
| `infrastructure.json` | CPU, memory, pod count, HPA scale events |

## Deployment (R8.5, R8.6)

Prometheus is deployed via the `kube-prometheus-stack` Helm chart.
The app is scraped via a `ServiceMonitor` defined in the app Helm chart.

### Step 1 — Add the Helm repository

```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update
```

### Step 2 — Install kube-prometheus-stack

```bash
helm upgrade --install kube-prometheus-stack prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --create-namespace \
  --set grafana.adminPassword=admin \
  --set prometheus.prometheusSpec.serviceMonitorSelectorNilUsesHelmValues=false
```

> **Why `serviceMonitorSelectorNilUsesHelmValues=false`?**
>
> By default `kube-prometheus-stack` configures Prometheus to only discover
> `ServiceMonitor` resources that were created by the **same Helm release**
> (i.e. those in the `monitoring` namespace with matching Helm labels).
> The app's `ServiceMonitor` lives in the `monitoring` namespace but is
> created by the **app Helm chart**, not by `kube-prometheus-stack` itself.
> Without this flag, Prometheus silently ignores the app's `ServiceMonitor`
> and the `/actuator/prometheus` endpoint is never scraped.
> Setting `serviceMonitorSelectorNilUsesHelmValues=false` clears the selector
> restriction so Prometheus discovers `ServiceMonitor` resources from **any**
> namespace regardless of which Helm release created them.
>
> In production, replace `grafana.adminPassword=admin` with a reference to a
> Kubernetes Secret:
> ```bash
> kubectl create secret generic grafana-admin \
>   --from-literal=admin-password='<STRONG_PASSWORD>' \
>   --namespace monitoring
> ```
> Then pass `--set grafana.admin.existingSecret=grafana-admin` instead.

### Step 3 — Enable ServiceMonitor in the app chart

`infrastructure/helm/myapp-chart/values-k8s.yaml` already sets
`monitoring.enabled: true`. Re-deploy the app release to apply it:

```bash
helm upgrade myapp infrastructure/helm/myapp-chart \
  --namespace myapp \
  --reuse-values \
  --set monitoring.enabled=true
```

### Step 4 — Verify (R8.7)

```bash
# Confirm ServiceMonitor resource exists
kubectl get servicemonitor -n monitoring
kubectl describe servicemonitor myapp-servicemonitor -n monitoring
# Check: endpoints[0].path=/actuator/prometheus, endpoints[0].port=http

# Wait for all monitoring pods to be Running/Ready
kubectl get pods -n monitoring --watch

# Port-forward and verify the scrape endpoint exposes custom metrics
kubectl port-forward svc/myapp 8080:8080 -n myapp &
curl -s localhost:8080/actuator/prometheus | grep -E \
  "items_created_total|items_deleted_total|items_fetch_duration_seconds"
# Expected: three metric families each with # HELP and # TYPE headers

# Port-forward Prometheus UI and confirm target is UP
kubectl port-forward svc/kube-prometheus-stack-prometheus 9090:9090 -n monitoring &
# Open http://localhost:9090/targets — myapp should show state=UP
# Run query: items_created_total — metric must be present (value may be 0)
```

> See `docs/monitoring.md` for the full operational runbook.
