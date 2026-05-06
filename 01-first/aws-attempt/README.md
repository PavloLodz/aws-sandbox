# aws-attempt

Spring Boot 3 REST API with PostgreSQL, ready for AWS deployment.

## Tech stack

- Java 21
- Spring Boot 3.2
- Spring Data JPA + Hibernate
- PostgreSQL (Flyway migrations)
- Testcontainers for integration tests

---

## Local development

### Prerequisites

- Java 21+
- Maven 3.9+
- Docker (for Testcontainers and local Postgres)

### Run locally

```bash
# Start a local Postgres
docker run -d \
  --name pg-local \
  -e POSTGRES_DB=aws_attempt \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  postgres:16-alpine

# Run the app with the local profile
mvn clear spring-boot:run -Dspring-boot.run.profiles=local
```

### Run tests

```bash
mvn clear test
```

---

## API endpoints

| Method | Path              | Description        |
|--------|-------------------|--------------------|
| GET    | /api/items        | List / search items |
| GET    | /api/items/{id}   | Get by ID           |
| POST   | /api/items        | Create item         |
| PUT    | /api/items/{id}   | Update item         |
| DELETE | /api/items/{id}   | Delete item         |
| GET    | /actuator/health  | Health check        |

### Example request

```bash
curl -X POST http://localhost:8080/api/items \
  -H "Content-Type: application/json" \
  -d '{"name":"My first item","description":"Hello AWS"}'
```
