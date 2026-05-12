# myapp

Spring Boot 3.2 REST API managing `Item` entities via PostgreSQL.
Part of the enterprise-platform learning path.

## Tech stack

- Java 21
- Spring Boot 3.2
- Spring Data JPA + Hibernate
- PostgreSQL (Flyway migrations)
- Testcontainers for integration tests

---

## Quick start (Docker Compose)

```bash
cp .env.example .env          # adjust values if needed
docker compose up --build
curl localhost:8080/api/items  # should return []
```

The startup sequence: Compose waits for PostgreSQL to pass `pg_isready` before
starting the `app` container (`depends_on: db: condition: service_healthy`).

---

## Run tests

```bash
./mvnw verify   # runs unit tests + integration tests via Testcontainers
```

---

## API

| Method | Path            | Description         |
|--------|-----------------|---------------------|
| GET    | /api/items      | List / search items |
| GET    | /api/items/{id} | Get by ID           |
| POST   | /api/items      | Create item         |
| PUT    | /api/items/{id} | Update item         |
| DELETE | /api/items/{id} | Delete item         |

Swagger UI: http://localhost:8080/swagger-ui.html

### Example request

```bash
curl -X POST http://localhost:8080/api/items \
  -H "Content-Type: application/json" \
  -d '{"name":"My first item","description":"Hello myapp"}'
```

---

## Health

| Endpoint                       | Purpose                    |
|--------------------------------|----------------------------|
| /actuator/health               | Overall health             |
| /actuator/health/liveness      | Kubernetes liveness probe  |
| /actuator/health/readiness     | Kubernetes readiness probe |

---

