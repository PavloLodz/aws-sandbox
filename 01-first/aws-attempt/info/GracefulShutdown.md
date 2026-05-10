#Graceful shutdown is triggered automatically — you don't call it directly. Here's every way it happens:

---

## In production (Kubernetes)

```bash
kubectl delete pod <pod-name> -n myapp
# or
kubectl rollout restart deployment/myapp -n myapp
```

Kubernetes sends **SIGTERM** to the JVM, which triggers the entire sequence automatically:
1. Spring sets readiness to `OUT_OF_SERVICE` → load balancer stops routing new requests
2. Tomcat finishes in-flight requests (up to 30s)
3. Spring context closes, beans destroyed
4. `@PreDestroy` logs "shutdown complete"
5. JVM exits with code 0
6. Kubernetes sees the pod gone and sends no SIGKILL

---

## Locally via the Actuator endpoint

The cleanest way to trigger it programmatically without killing the process:

```bash
# First expose the shutdown endpoint (it's off by default for security)
# Add to application.properties temporarily:
# management.endpoint.shutdown.enabled=true
# management.endpoints.web.exposure.include=health,info,shutdown

curl -X POST http://localhost:8080/actuator/shutdown
```

You'll see in the logs:
```
Spring context 'application' is closing — graceful shutdown initiated
Application shutdown complete — JVM is exiting
```

> Don't leave `shutdown` exposed in production — it's unauthenticated by default.

---

## Locally via the terminal

If running with `java -jar` or `./mvnw spring-boot:run`:

```bash
# Find the PID
lsof -i :8080

# Send SIGTERM (graceful — same signal Kubernetes sends)
kill -15 <pid>

# NOT this — this is SIGKILL, bypasses graceful shutdown entirely
kill -9 <pid>
```

If running with `docker compose`:

```bash
# Graceful — sends SIGTERM, waits for the container to stop
docker compose stop

# NOT this — sends SIGKILL immediately
docker compose kill
```

---

## How to verify it actually worked gracefully

Watch the logs during shutdown — you should see all three lines from `GracefulShutdownConfig` in order:

```
INFO  Spring context 'application' is closing — graceful shutdown initiated
INFO  Tomcat received SIGTERM — waiting for in-flight requests to complete
INFO  Application shutdown complete — JVM is exiting
```

If you see the last line, shutdown was clean. If the process disappears without it, it was killed by SIGKILL before graceful shutdown could complete — increase `terminationGracePeriodSeconds` in your Kubernetes Deployment.

---

## Quick summary

| Trigger | Signal | Graceful? |
|---|---|---|
| `kubectl delete pod` | SIGTERM | ✅ Yes |
| `kubectl rollout restart` | SIGTERM | ✅ Yes |
| `curl -X POST /actuator/shutdown` | internal | ✅ Yes |
| `kill -15 <pid>` | SIGTERM | ✅ Yes |
| `docker compose stop` | SIGTERM | ✅ Yes |
| `kill -9 <pid>` | SIGKILL | ❌ No |
| `docker compose kill` | SIGKILL | ❌ No |
| Pod exceeds `terminationGracePeriodSeconds` | SIGKILL | ❌ No |
