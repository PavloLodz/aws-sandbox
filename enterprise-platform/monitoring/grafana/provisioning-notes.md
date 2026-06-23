# Grafana Dashboard Provisioning

Dashboards in `monitoring/grafana/dashboards/` are provisioned automatically
into Grafana via the `kube-prometheus-stack` sidecar mechanism — no manual
import through the Grafana UI is required (R8.13).

## How it works

1. Dashboard JSON files live in Git under `monitoring/grafana/dashboards/`
2. Each JSON file is loaded into a Kubernetes ConfigMap labelled `grafana_dashboard=1`
3. The Grafana sidecar container watches for ConfigMaps with that label across
   all namespaces (`searchNamespace: ALL`) and hot-loads them into Grafana
4. Dashboards appear in Grafana within ~30 seconds of the ConfigMap being applied
5. No Grafana pod restart is required

## Step 1 — Enable sidecar in kube-prometheus-stack

Include these Helm values when deploying or upgrading `kube-prometheus-stack`:

```yaml
grafana:
  sidecar:
    dashboards:
      enabled: true
      label: grafana_dashboard
      labelValue: "1"
      searchNamespace: ALL
```

> **Why `searchNamespace: ALL`?**
> Allows dashboard ConfigMaps to live in any namespace — consistent with
> multi-namespace cluster layouts where the app (in `myapp`) and the
> monitoring stack (in `monitoring`) are separated. Without this, only
> ConfigMaps in the same namespace as Grafana are discovered.

As a `--set` argument:

```bash
helm upgrade --install kube-prometheus-stack prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --set grafana.sidecar.dashboards.enabled=true \
  --set grafana.sidecar.dashboards.label=grafana_dashboard \
  --set grafana.sidecar.dashboards.labelValue=1 \
  --set grafana.sidecar.dashboards.searchNamespace=ALL
```

## Step 2 — Create labelled ConfigMaps

Run these three commands from the repository root after the JSON files exist.
Each command creates a ConfigMap from the JSON file and applies the required
`grafana_dashboard=1` label in a single pipeline:

```bash
# Application dashboard — HTTP metrics, latency, JVM, DB connections
kubectl create configmap grafana-dashboard-application \
  --from-file=application.json=monitoring/grafana/dashboards/application.json \
  --namespace monitoring \
  --dry-run=client -o yaml | \
  kubectl label --local -f - grafana_dashboard=1 -o yaml | \
  kubectl apply -f -

# Business dashboard — item create/delete rates and fetch duration histograms
kubectl create configmap grafana-dashboard-business \
  --from-file=business.json=monitoring/grafana/dashboards/business.json \
  --namespace monitoring \
  --dry-run=client -o yaml | \
  kubectl label --local -f - grafana_dashboard=1 -o yaml | \
  kubectl apply -f -

# Infrastructure dashboard — CPU, memory, pod count, HPA scale events
kubectl create configmap grafana-dashboard-infrastructure \
  --from-file=infrastructure.json=monitoring/grafana/dashboards/infrastructure.json \
  --namespace monitoring \
  --dry-run=client -o yaml | \
  kubectl label --local -f - grafana_dashboard=1 -o yaml | \
  kubectl apply -f -
```

The Grafana sidecar hot-loads the dashboards within ~30 seconds — no manual
import and no Grafana pod restart is required (R8.13).

## Step 3 — Verify dashboards are loaded

```bash
# Confirm ConfigMaps exist with the correct label
kubectl get configmap -l grafana_dashboard=1 -n monitoring

# Port-forward Grafana and open the dashboards page
kubectl port-forward svc/kube-prometheus-stack-grafana 3000:80 -n monitoring
# Open: http://localhost:3000/dashboards
# Login: admin / <grafana.adminPassword from helm values>
```

Three dashboards should appear under a `myapp` or `General` folder:
- **MyApp — Application** — request rate, error rate, latency percentiles, JVM heap, DB connections
- **MyApp — Business** — items created/deleted rates and totals, fetch duration p50/p95/p99
- **MyApp — Infrastructure** — CPU, memory, pod count, HPA replicas and scale events

## Updating dashboards

To update a dashboard after editing the JSON file, re-run the corresponding
`kubectl create configmap … | kubectl apply -f -` command above. The sidecar
picks up the change within ~30 seconds.
