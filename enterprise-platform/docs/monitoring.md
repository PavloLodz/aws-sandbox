# Monitoring — Topic 8

This runbook covers the end-to-end observability stack for the `myapp`
Spring Boot application. The flow is: Spring Boot exposes
`/actuator/prometheus` via Micrometer, Prometheus scrapes it through a
`ServiceMonitor`, alert conditions are defined as `PrometheusRule` resources
that fire through Alertmanager to Slack, and Grafana visualises all metrics
across three provisioned dashboards — with no manual steps between a code push
and a live dashboard.

---

## Table of Contents

1. [Overview](#1-overview)
2. [Prerequisites](#2-prerequisites)
3. [Application Metrics](#3-application-metrics)
4. [Prometheus](#4-prometheus)
5. [Alertmanager](#5-alertmanager)
6. [Grafana Dashboards](#6-grafana-dashboards)
7. [Troubleshooting](#7-troubleshooting)
8. [Monitoring Commands Reference](#8-monitoring-commands-reference)

---

## 1. Overview

The observability stack follows this data flow:

```
ItemService (Counter / Timer)
    ↓  Micrometer
/actuator/prometheus  (HTTP scrape endpoint)
    ↓  ServiceMonitor (30 s interval)
Prometheus  →  PrometheusRule  →  Alertmanager  →  Slack #alerts-dev
    ↓
Grafana (3 dashboards, auto-provisioned via ConfigMap sidecar)
```

| Dashboard file | Title | Shows |
|---|---|---|
| `monitoring/grafana/dashboards/application.json` | MyApp — Application | Request rate, error rate, p95/p99 latency, JVM heap, DB connections, GC pause |
| `monitoring/grafana/dashboards/business.json` | MyApp — Business | Items created/deleted rates and totals, fetch duration p50/p95/p99 |
| `monitoring/grafana/dashboards/infrastructure.json` | MyApp — Infrastructure | CPU, memory, pod count, HPA replicas and scale events |

---

## 2. Prerequisites

Part 1 (Steps 1–5 of the monitoring implementation) must be complete before
deploying the alerting rules and dashboards covered here. Specifically,
`/actuator/prometheus` must already return the three custom metrics
(`items_created_total`, `items_deleted_total`, `items_fetch_duration_seconds`)
before applying `PrometheusRule` resources.

| Tool | Minimum version | Check command |
|---|---|---|
| `kubectl` | ≥ 1.27 | `kubectl version --client` |
| `helm` | ≥ 3.14 | `helm version` |
| `tkn` | optional | `tkn version` |

`kube-prometheus-stack` must be running in the `monitoring` namespace — see
Section 4.1 for installation.

---

## 3. Application Metrics

### 3.1 Dependency (`pom.xml`)

The `micrometer-registry-prometheus` dependency auto-configures the
`/actuator/prometheus` scrape endpoint. It is managed by the
`spring-boot-starter-parent` BOM — no explicit `<version>` tag is needed:

```xml
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-registry-prometheus</artifactId>
  <!-- version managed by spring-boot-starter-parent BOM -->
</dependency>
```

Adding an explicit version would override the BOM and risk skew with other
Micrometer modules already on the classpath via `spring-boot-starter-actuator`.

### 3.2 Actuator configuration (`application.properties`)

Two lines expose the scrape endpoint:

```properties
# Prometheus scrape endpoint — required by ServiceMonitor (R8.2)
management.endpoints.web.exposure.include=health,info,prometheus
management.endpoint.prometheus.enabled=true
```

All other actuator endpoint settings (health probes, graceful shutdown) are
unchanged.

### 3.3 Custom metrics in `ItemService`

Three meters are registered in `ItemService` via a `@PostConstruct` method
(`initMetrics()`). The method is package-private so it can be called directly
in unit tests without the Spring lifecycle:

| Micrometer name | Prometheus name | Type | Incremented when |
|---|---|---|---|
| `items.created.total` | `items_created_total` | Counter | After `itemRepository.save()` succeeds in `create()` |
| `items.deleted.total` | `items_deleted_total` | Counter | After `itemRepository.deleteById()` succeeds in `delete()` |
| `items.fetch.duration` | `items_fetch_duration_seconds` | Timer | Wraps `itemRepository.findAll()` in `findAll()` via `Timer.record(Supplier)` |

`Timer.record(Supplier)` is used instead of `Timer.start()/stop()` to ensure
the timer stops automatically even if the repository throws — no `Timer.Sample`
is leaked on exception.

### 3.4 Verifying the `/actuator/prometheus` endpoint

```bash
# Start the app locally (requires Postgres — use docker compose or Testcontainers)
cd application && mvn spring-boot:run

# Confirm the three custom metrics are present
curl -s localhost:8080/actuator/prometheus | grep -E \
  "items_created_total|items_deleted_total|items_fetch_duration_seconds"
```

Expected output — three metric families each with `# HELP` and `# TYPE` headers:

```
# HELP items_created_total Total number of items created
# TYPE items_created_total counter
items_created_total_total 0.0
# HELP items_deleted_total Total number of items deleted
# TYPE items_deleted_total counter
items_deleted_total_total 0.0
# HELP items_fetch_duration_seconds Time taken by ItemRepository.findAll()
# TYPE items_fetch_duration_seconds summary
...
```

---

## 4. Prometheus

### 4.1 `kube-prometheus-stack` installation

```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

helm upgrade --install kube-prometheus-stack prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --create-namespace \
  --set grafana.adminPassword=admin \
  --set prometheus.prometheusSpec.serviceMonitorSelectorNilUsesHelmValues=false
```

> **Why `serviceMonitorSelectorNilUsesHelmValues=false`?**
> By default Prometheus only discovers `ServiceMonitor` resources created by
> the same Helm release. The app's `ServiceMonitor` is created by the app Helm
> chart, not by `kube-prometheus-stack`. Without this flag the app's scrape
> target is silently ignored. Setting it to `false` clears the selector
> restriction so Prometheus discovers `ServiceMonitor` resources from any
> namespace regardless of origin.
>
> In production, use a Kubernetes Secret for the Grafana password:
> ```bash
> kubectl create secret generic grafana-admin \
>   --from-literal=admin-password='<STRONG_PASSWORD>' --namespace monitoring
> # Then pass: --set grafana.admin.existingSecret=grafana-admin
> ```

### 4.2 ServiceMonitor (scrape config)

The `ServiceMonitor` is defined in the app Helm chart template
(`infrastructure/helm/myapp-chart/templates/servicemonitor.yaml`) and enabled
via `values-k8s.yaml`:

```bash
# Enable ServiceMonitor in the app chart
helm upgrade myapp infrastructure/helm/myapp-chart \
  --namespace myapp --reuse-values --set monitoring.enabled=true

# Confirm the ServiceMonitor resource exists
kubectl get servicemonitor -n monitoring
kubectl describe servicemonitor myapp-servicemonitor -n monitoring
```

Scrape configuration:
- **Path:** `/actuator/prometheus`
- **Port:** `http`
- **Interval:** `30s`
- **Scrape timeout:** `10s`

### 4.3 Alerting rules

Three `PrometheusRule` resources are defined in `monitoring/prometheus/rules/`:

| File | Alert name | Severity | `for` | Condition |
|---|---|---|---|---|
| `high-error-rate.yaml` | `HighErrorRate` | warning | 5m | 5xx rate > 1% of total requests |
| `pod-not-ready.yaml` | `PodNotReady` | critical | 2m | Any pod in `myapp` namespace not Ready |
| `high-memory-usage.yaml` | `HighJvmHeapUsage` | warning | 5m | JVM heap > 80% of max heap |

Apply all three rules:

```bash
kubectl apply -f monitoring/prometheus/rules/
```

> All three files carry `release: prometheus` in `metadata.labels`.
> This label is required by `kube-prometheus-stack`'s default `ruleSelector`.
> Without it the Prometheus Operator silently ignores the rule.

### 4.4 Verifying rules are loaded

```bash
# Confirm PrometheusRule resources exist
kubectl get prometheusrule -n monitoring

# Port-forward Prometheus UI
kubectl port-forward svc/kube-prometheus-stack-prometheus 9090:9090 -n monitoring
# Open: http://localhost:9090/rules
# → All three alert names should appear under Status → Rules with state Inactive
```

---

## 5. Alertmanager

### 5.1 Configuration overview

`monitoring/alertmanager/config.yaml` defines:

- **`global`**: `resolve_timeout: 5m`
- **Route tree**: alerts grouped by `alertname` + `severity`; `group_wait: 30s`, `group_interval: 5m`, default `repeat_interval: 12h`
- **Severity child routes**: critical re-notifies every 1h; warning re-notifies every 6h
- **Receiver**: `slack-notifications` — sends to `#alerts-dev` via Incoming Webhook
- **`inhibit_rules`**: when a `critical` alert fires, suppresses `warning` alerts with the same `alertname` — prevents duplicate notifications for the same root cause

### 5.2 Slack webhook setup (real vs dummy)

The committed file uses a dummy webhook URL (`DUMMY/DUMMY/DUMMYDUMMYDUMMY`).
This is intentional — it keeps the learning workflow self-contained without
requiring a real Slack workspace.

To enable real Slack notifications:

1. Go to `https://api.slack.com/apps` → **Create New App** → **Incoming Webhooks** → **Activate**
2. Add a webhook to the target channel and copy the URL (`https://hooks.slack.com/services/T.../B.../...`)
3. Store it as a Kubernetes Secret — never commit the real URL to Git:
   ```bash
   kubectl create secret generic alertmanager-slack \
     --from-literal=webhook-url='https://hooks.slack.com/services/...' \
     --namespace monitoring
   ```
4. Reference it in the `kube-prometheus-stack` Helm values:
   ```yaml
   alertmanager:
     alertmanagerSpec:
       configSecret: alertmanager-slack
   ```
5. After applying, reload Alertmanager:
   ```bash
   kubectl rollout restart statefulset \
     alertmanager-kube-prometheus-stack-alertmanager -n monitoring
   ```

### 5.3 Applying the config

```bash
# Apply config.yaml as a Kubernetes Secret for Alertmanager to consume
kubectl create secret generic alertmanager-main \
  --from-file=alertmanager.yaml=monitoring/alertmanager/config.yaml \
  --namespace monitoring \
  --dry-run=client -o yaml | kubectl apply -f -

# Verify the secret was created
kubectl get secret alertmanager-main -n monitoring
```

---

## 6. Grafana Dashboards

### 6.1 Provisioning mechanism

Dashboards are provisioned via the `kube-prometheus-stack` Grafana sidecar.
No manual import through the Grafana UI is required.

**Required Helm values** (include when installing or upgrading `kube-prometheus-stack`):

```yaml
grafana:
  sidecar:
    dashboards:
      enabled: true
      label: grafana_dashboard
      labelValue: "1"
      searchNamespace: ALL
```

`searchNamespace: ALL` allows dashboard ConfigMaps to exist in any namespace —
consistent with multi-namespace layouts where the app (`myapp`) and monitoring
stack (`monitoring`) are separated.

**Apply the three dashboard ConfigMaps** from the repository root:

```bash
kubectl create configmap grafana-dashboard-application \
  --from-file=application.json=monitoring/grafana/dashboards/application.json \
  --namespace monitoring --dry-run=client -o yaml | \
  kubectl label --local -f - grafana_dashboard=1 -o yaml | kubectl apply -f -

kubectl create configmap grafana-dashboard-business \
  --from-file=business.json=monitoring/grafana/dashboards/business.json \
  --namespace monitoring --dry-run=client -o yaml | \
  kubectl label --local -f - grafana_dashboard=1 -o yaml | kubectl apply -f -

kubectl create configmap grafana-dashboard-infrastructure \
  --from-file=infrastructure.json=monitoring/grafana/dashboards/infrastructure.json \
  --namespace monitoring --dry-run=client -o yaml | \
  kubectl label --local -f - grafana_dashboard=1 -o yaml | kubectl apply -f -
```

The Grafana sidecar hot-loads dashboards within ~30 seconds — no Grafana pod
restart is required.

### 6.2 Dashboard reference

| Dashboard | File | Panels |
|---|---|---|
| **MyApp — Application** | `application.json` | Request rate (by URI/method), Error rate 5xx%, p95 latency, p99 latency, Active DB connections, JVM heap used, GC pause time |
| **MyApp — Business** | `business.json` | Items created rate/min, Items deleted rate/min, Items created total (stat), Items deleted total (stat), Fetch duration p50/p95/p99 |
| **MyApp — Infrastructure** | `infrastructure.json` | CPU usage (by pod), Memory usage (by pod), Ready pod count (stat, red/green threshold), HPA desired replicas, HPA scale events, Pod shutdown duration |

All panels use a `${datasource}` template variable — no hardcoded Grafana
datasource UID — so dashboards work across any Prometheus datasource
configuration.

### 6.3 Accessing Grafana

```bash
kubectl port-forward svc/kube-prometheus-stack-grafana 3000:80 -n monitoring
```

Open `http://localhost:3000/dashboards` in a browser.

| Field | Value |
|---|---|
| Username | `admin` |
| Password | Value of `grafana.adminPassword` set at install time (default: `admin`) |

In production, use `grafana.admin.existingSecret` to reference a Kubernetes
Secret instead of the `grafana.adminPassword` Helm value.

---

## 7. Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `/actuator/prometheus` returns 404 | `prometheus` endpoint not in the exposure list | Add `prometheus` to `management.endpoints.web.exposure.include` in `application.properties`; confirm `management.endpoint.prometheus.enabled=true` |
| Custom metrics absent from `/actuator/prometheus` | `@PostConstruct initMetrics()` not called / `MeterRegistry` not injected | Verify `private final MeterRegistry meterRegistry` is declared in `ItemService` and `initMetrics()` is annotated `@PostConstruct`; run the unit tests to confirm `SimpleMeterRegistry` can register all three meters |
| Prometheus not scraping the app | `ServiceMonitor` not found by Prometheus Operator | Confirm `release: prometheus` label is present on the `ServiceMonitor`; confirm `serviceMonitorSelectorNilUsesHelmValues=false` was set when deploying `kube-prometheus-stack`; check `kubectl describe servicemonitor -n monitoring` |
| Grafana dashboards not appearing | ConfigMaps missing `grafana_dashboard=1` label or sidecar not scanning all namespaces | Re-apply ConfigMaps using the `kubectl label --local` pipeline; verify `grafana.sidecar.dashboards.searchNamespace: ALL` in Helm values; check Grafana sidecar logs: `kubectl logs -l app.kubernetes.io/name=grafana -c grafana-sc-dashboard -n monitoring` |
| Alerts not firing in Prometheus | `PrometheusRule` missing `release: prometheus` label | Add `release: prometheus` to `metadata.labels` and re-apply; check Prometheus UI → Status → Rules — all three alert names must appear with state `Inactive` or `Firing` |
| Slack notifications not delivered | Dummy webhook URL in use / Alertmanager not reloaded after config change | Replace the `DUMMY/DUMMY/DUMMYDUMMYDUMMY` placeholder with a real Slack webhook URL stored in a Kubernetes Secret (see Section 5.2); restart the Alertmanager statefulset after applying the secret |
| `items_created_total` always 0 | Counter never incremented — exception thrown before `increment()` reaches execution | Verify `itemsCreatedCounter.increment()` is the line immediately after `ItemResponse.from(itemRepository.save(item))` returns — never before, and never inside an exception handler |

---

## 8. Monitoring Commands Reference

```bash
# ── Prometheus ──────────────────────────────────────────────────────────────
# List PrometheusRule resources
kubectl get prometheusrule -n monitoring

# Port-forward Prometheus UI (Targets, Rules, Alerts)
kubectl port-forward svc/kube-prometheus-stack-prometheus 9090:9090 -n monitoring
# Open: http://localhost:9090/targets   — confirm myapp state=UP
# Open: http://localhost:9090/rules     — confirm all three alert rules loaded
# Open: http://localhost:9090/alerts    — confirm alert states

# Query custom metric directly in Prometheus
# http://localhost:9090/graph?g0.expr=items_created_total

# ── ServiceMonitor ──────────────────────────────────────────────────────────
kubectl get servicemonitor -n monitoring
kubectl describe servicemonitor myapp-servicemonitor -n monitoring

# ── Grafana ─────────────────────────────────────────────────────────────────
# List provisioned dashboard ConfigMaps
kubectl get configmap -l grafana_dashboard=1 -n monitoring

# Port-forward Grafana
kubectl port-forward svc/kube-prometheus-stack-grafana 3000:80 -n monitoring
# Open: http://localhost:3000/dashboards

# ── Alertmanager ────────────────────────────────────────────────────────────
kubectl port-forward svc/kube-prometheus-stack-alertmanager 9093:9093 -n monitoring
# Open: http://localhost:9093   — view active alerts and silences

# ── All monitoring pods ─────────────────────────────────────────────────────
kubectl get pods -n monitoring
kubectl get pods -n monitoring --watch

# ── Apply all alerting rules ────────────────────────────────────────────────
kubectl apply -f monitoring/prometheus/rules/

# ── Apply a single dashboard ConfigMap (after editing JSON) ─────────────────
kubectl create configmap grafana-dashboard-application \
  --from-file=application.json=monitoring/grafana/dashboards/application.json \
  --namespace monitoring --dry-run=client -o yaml | \
  kubectl label --local -f - grafana_dashboard=1 -o yaml | kubectl apply -f -
```

---

## Acceptance Criteria

- [ ] `/actuator/prometheus` returns `items_created_total`, `items_deleted_total`, and `items_fetch_duration_seconds` metric families with `# HELP` and `# TYPE` headers
- [ ] Calling `POST /items` increments `items_created_total` by 1 (observable in the Prometheus expression browser)
- [ ] Calling `DELETE /items/{id}` for an existing item increments `items_deleted_total` by 1
- [ ] Calling `GET /items` records a timing observation in `items_fetch_duration_seconds` (timer count increases)
- [ ] Prometheus target for `myapp` shows state `UP` in the Targets UI (`http://localhost:9090/targets`)
- [ ] All three `PrometheusRule` resources appear under `Status → Rules` in the Prometheus UI with state `Inactive` (not Firing)
- [ ] Firing a test alert (e.g. by temporarily lowering the `HighErrorRate` threshold) results in a Slack message in `#alerts-dev` when a real webhook URL is configured
- [ ] All three Grafana dashboards (Application, Business, Infrastructure) appear in the Grafana UI at `http://localhost:3000/dashboards` without manual import
- [ ] `items_created_total` is visible and queryable in the Business dashboard after at least one item has been created via `POST /items`
- [ ] No real credentials (Slack webhook URL, registry password, PAT) appear in any committed file under `monitoring/`

---

## Key Design Decisions

| Decision | Rationale |
|---|---|
| `release: prometheus` label on `PrometheusRule` | Required by `kube-prometheus-stack`'s default `ruleSelector` — missing it causes the Prometheus Operator to silently ignore the rule; the alert never appears in the Prometheus UI |
| `inhibit_rules` in Alertmanager config | Suppresses `warning` alerts when a `critical` alert fires with the same `alertname` — prevents duplicate Slack notifications where a single pod crash triggers both `PodNotReady` (critical) and `HighErrorRate` (warning) |
| Dummy Slack webhook URL committed to Git | Keeps the learning workflow self-contained without requiring a real Slack workspace; the full setup path is documented in the header comment; prevents accidental credential exposure |
| ConfigMap + sidecar provisioning for Grafana | Dashboard JSON files live in Git and are version-controlled; `kubectl` creates ConfigMaps; Grafana sidecar hot-loads them within ~30s — no manual import step, no Grafana pod restart required |
| `searchNamespace: ALL` on Grafana sidecar | Allows dashboard ConfigMaps to exist in any namespace — consistent with multi-namespace cluster layouts where the app (`myapp`) and monitoring (`monitoring`) namespaces are separate |
| Three separate dashboard files (application / business / infrastructure) | Separates concerns by audience: on-call engineers see latency/error rate (Application), product teams see item throughput (Business), platform engineers see CPU/memory/HPA (Infrastructure) |
