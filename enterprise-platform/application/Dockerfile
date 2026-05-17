# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS builder

# Set working directory inside the container
# All following commands run from /app
WORKDIR /app

# Copy only pom.xml first
# This improves Docker layer caching because dependencies
# usually change less often than source code
COPY pom.xml .

# Download all Maven dependencies in advance
# "-q" = quiet mode for cleaner logs
# This layer is cached unless pom.xml changes
RUN mvn dependency:go-offline -q
# Copy application source code into container
COPY src ./src

RUN mvn package -DskipTests -q

# ------------------------------------------------------------
# RUNTIME STAGE
# This stage creates a lightweight production image
# containing only the JRE and the built application.
# ------------------------------------------------------------

FROM eclipse-temurin:21-jre-alpine
# Set working directory for runtime container
WORKDIR /app

# Create a dedicated non-root user and group
# Running containers as non-root improves security
#
# addgroup -S      -> create system group
# adduser  -S      -> create system user
# -G appgroup      -> assign user to group
# -u 1000          -> set explicit UID 1000
#
# UID 1000 is often required for:
# - OpenShift Security Context Constraints (SCC)
# - enterprise Kubernetes policies
# - security compliance requirements
RUN addgroup -S appgroup && adduser -S appuser -G appgroup -u 1000

# Copy JAR as root so ownership is predictable, then switch user
COPY --from=builder /app/target/aws-attempt-*.jar app.jar

# Switch container execution to non-root user
USER appuser

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=docker

# Configure container health check
#
# Docker/Kubernetes periodically calls this endpoint
# to verify the application is healthy
#
# --interval=30s -> run every 30 seconds
# --timeout=5s   -> fail if response takes >5s
# --retries=3    -> mark unhealthy after 3 failures
#
# BusyBox wget is already available in Alpine image
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

# Application startup command
#
# CMD (not ENTRYPOINT) allows overriding(!!!) with docker run arguments.
# For example: docker run --rm myapp:local whoami  -> prints "appuser"
# Normal start: docker run myapp:local             -> starts Spring Boot
#
# -Djava.security.egd=file:/dev/./urandom
# improves startup speed by using non-blocking entropy source
CMD ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
