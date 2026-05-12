package pl.ldz.example;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

/**
 * Base class for REAL AWS integration tests.
 *
 * <p>Uses actual AWS services — RDS PostgreSQL and S3 — so it requires
 * the following environment variables to be set before running:
 *
 * <pre>
 *   RDS_HOSTNAME   — RDS endpoint, e.g. mydb.xxxx.eu-central-1.rds.amazonaws.com
 *   RDS_PORT       — usually 5432
 *   RDS_DB_NAME    — database name
 *   RDS_USERNAME   — database user
 *   RDS_PASSWORD   — database password
 *   S3_BUCKET      — existing S3 bucket name
 *   S3_REGION      — AWS region, e.g. eu-central-1
 * </pre>
 *
 * <p>AWS credentials must be available via one of:
 * <ul>
 *   <li>EC2 IAM role (recommended on EC2 / in CI)</li>
 *   <li>{@code AWS_ACCESS_KEY_ID} + {@code AWS_SECRET_ACCESS_KEY} env vars</li>
 *   <li>{@code ~/.aws/credentials} file</li>
 * </ul>
 *
 * <p>Tests are <strong>skipped automatically</strong> (via {@link AwsEnvironmentCondition})
 * when any required env var is absent — the condition runs BEFORE Spring loads the
 * ApplicationContext, so there are no unresolved placeholder errors during a local build.
 *
 * <p>How to run:
 * <pre>
 *   export RDS_HOSTNAME=mydb.xxxx.eu-central-1.rds.amazonaws.com
 *   export RDS_PORT=5432
 *   export RDS_DB_NAME=aws_attempt
 *   export RDS_USERNAME=postgres
 *   export RDS_PASSWORD=secret
 *   export S3_BUCKET=my-app-bucket
 *   export S3_REGION=eu-central-1
 *   mvn verify -Paws-it
 * </pre>
 */
@ExtendWith(AwsEnvironmentCondition.class)
@ContextConfiguration(initializers = AwsPropertiesInitializer.class)
@TestPropertySource(properties = {
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration"
})
public abstract class AbstractAwsIT {
  // No containers — all config comes from application-aws.properties + env vars
  // injected by AwsPropertiesInitializer.
  // AWS SDK credentials: EC2 IAM role or AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY env vars.
}
