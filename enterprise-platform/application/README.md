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
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### Run tests

```bash
./mvnw test
```

---

## AWS deployment options

### Option A – Elastic Beanstalk (simplest)

1. Build the JAR:
   ```bash
   ./mvnw package -DskipTests
   ```
2. Create an Elastic Beanstalk environment (Java 21 platform).
3. Attach an RDS PostgreSQL instance — Beanstalk injects
   `RDS_HOSTNAME`, `RDS_PORT`, `RDS_DB_NAME`, `RDS_USERNAME`, `RDS_PASSWORD`
   automatically as environment variables.
4. Set the environment variable `SPRING_PROFILES_ACTIVE=aws`.
5. Upload `target/aws-attempt-*.jar`.

### Option B – ECS (container)

1. Build and push the image:
   ```bash
   docker build -t aws-attempt .
   docker tag aws-attempt <account>.dkr.ecr.<region>.amazonaws.com/aws-attempt:latest
   aws ecr get-login-password | docker login --username AWS --password-stdin <account>.dkr.ecr.<region>.amazonaws.com
   docker push <account>.dkr.ecr.<region>.amazonaws.com/aws-attempt:latest
   ```
2. Create an ECS Task Definition with environment variables:
   ```
   RDS_HOSTNAME  → your RDS endpoint
   RDS_PORT      → 5432
   RDS_DB_NAME   → aws_attempt
   RDS_USERNAME  → (from Secrets Manager)
   RDS_PASSWORD  → (from Secrets Manager)
   ```
3. Create an ECS Service behind an Application Load Balancer.
4. ALB health check path: `/actuator/health`

### Option C – AWS Lambda (serverless, SnapStart)

Add the `spring-cloud-function-web` dependency and enable SnapStart on the Lambda function for fast cold starts.

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
