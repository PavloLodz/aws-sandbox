# Docker — Topic 1

How to build the `myapp` container image and run the full stack locally with
Docker Compose.

---

## Prerequisites

Confirm these are in place before building anything:

```bash
docker --version          # Docker Engine ≥ 24 (not Desktop on Linux)
docker compose version    # v2 plugin (not standalone docker-compose v1)
./mvnw verify             # all unit + integration tests must pass before building
```

Never build the Docker image before `./mvnw verify` passes — this is enforced in
every CI pipeline (Topic 7).

---

## Building the image

```bash
cd application
docker build -t myapp:local .
```

Verify the result:

```bash
docker image ls myapp:local         # confirm SIZE < 250 MB
docker run --rm myapp:local whoami  # confirm output: appuser
docker run --rm myapp:local id      # confirm output: uid=1000(appuser)
```

The image is built in two stages. The first stage (`builder`) compiles the JAR
using `maven:3.9-eclipse-temurin-21`. The second stage (`runtime`) copies only the
JAR into `eclipse-temurin:21-jre-alpine`, discarding the Maven toolchain and all
downloaded dependencies. This keeps the final image under 250 MB.

---

## Running the full stack

```bash
cd application
cp .env.example .env    # first time only — adjust values if needed
docker compose up --build
```

**Startup sequence:** Compose starts the `db` container first and waits for
PostgreSQL to pass `pg_isready` before starting `app`
(`depends_on: db: condition: service_healthy`). This prevents the race condition
where the application tries to connect before the database is ready.

To run in the background:

```bash
docker compose up --build -d
docker compose logs -f app    # stream app logs
```

---

## Verifying endpoints

Once both containers are healthy, run each check:

```bash
curl -s localhost:8080/api/items
# expected: [] (empty JSON array)

curl -s localhost:8080/actuator/health
# expected: {"status":"UP"}

curl -s localhost:8080/actuator/health/liveness
# expected: {"status":"UP","components":{"livenessState":{"status":"UP"}}}

curl -s localhost:8080/actuator/health/readiness
# expected: {"status":"UP","components":{"readinessState":{"status":"UP"}}}

curl -s -o /dev/null -w "%{http_code}" localhost:8080/swagger-ui.html
# expected: 302
```

Swagger UI is available at: http://localhost:8080/swagger-ui.html

---

## Overriding the Spring profile

The default profile inside the container is `docker` (set via
`ENV SPRING_PROFILES_ACTIVE=docker` in the Dockerfile). Override it at runtime:

```bash
# Via docker run
docker run -e SPRING_PROFILES_ACTIVE=local -p 8080:8080 myapp:local

# Via environment block in docker-compose.yml
environment:
  SPRING_PROFILES_ACTIVE: local
```

Available profiles:

| Profile  | Datasource                              | When to use                          |
|----------|-----------------------------------------|--------------------------------------|
| `docker` | `db:5432` (Compose service name)        | Inside Docker Compose (default)      |
| `local`  | `localhost:5432` (host machine)         | Running JAR directly, not via Compose|

---

## Teardown

```bash
docker compose down        # stop containers, keep pgdata volume
docker compose down -v     # stop containers AND delete pgdata volume
docker volume ls | grep pgdata    # should return nothing after -v
```

Use `down` (without `-v`) during iterative development to preserve database state
between sessions — Flyway migrations do not re-run on data that already exists.

Use `down -v` for a completely clean slate: the `pgdata` volume is removed and
PostgreSQL starts fresh on the next `up`. Use this before running integration tests
or when troubleshooting migration issues.

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `app` exits immediately on first run | DB container not healthy yet | Confirm `condition: service_healthy` in `depends_on`; increase `start_period` in the DB healthcheck |
| `Connection refused` on port 5432 | `db` container not started | `docker compose ps` — check db health status |
| `password authentication failed` | `.env` not present or wrong values | Confirm `.env` exists and values match `DB_USERNAME`/`DB_PASSWORD` |
| Image over 250 MB | `.dockerignore` not picked up | Confirm `application/.dockerignore` exists (not a symlink) |
| `wget: bad address` in HEALTHCHECK | Wrong host in healthcheck command | Healthcheck uses `localhost` — correct inside the container |
