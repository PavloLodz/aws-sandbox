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

## Deployment

Prometheus is deployed via the `kube-prometheus-stack` Helm chart.
The app is scraped via a `ServiceMonitor` defined in the Helm chart.
