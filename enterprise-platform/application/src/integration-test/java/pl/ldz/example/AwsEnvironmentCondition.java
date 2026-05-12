package pl.ldz.example;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * JUnit 5 {@link ExecutionCondition} that disables a test class entirely
 * when required AWS environment variables are not set.
 *
 * <p>Unlike {@code @EnabledIfEnvironmentVariable}, this condition is evaluated
 * <strong>before</strong> the Spring ApplicationContext is created, so Spring
 * never tries to resolve unset placeholders like {@code ${RDS_HOSTNAME}}.
 *
 * <p>Required env vars: RDS_HOSTNAME, RDS_PORT, RDS_DB_NAME,
 * RDS_USERNAME, RDS_PASSWORD, S3_BUCKET, S3_REGION.
 */
public class AwsEnvironmentCondition implements ExecutionCondition {

  private static final String[] REQUIRED_VARS = {
      "RDS_HOSTNAME", "RDS_PORT", "RDS_DB_NAME",
      "RDS_USERNAME", "RDS_PASSWORD",
      "S3_BUCKET", "S3_REGION"
  };

  @Override
  public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
    for (String var : REQUIRED_VARS) {
      String value = System.getenv(var);
      if (value == null || value.isBlank()) {
        return ConditionEvaluationResult.disabled(
            "AWS integration test skipped: environment variable '" + var + "' is not set. "
                + "Set all required vars (RDS_HOSTNAME, RDS_PORT, RDS_DB_NAME, "
                + "RDS_USERNAME, RDS_PASSWORD, S3_BUCKET, S3_REGION) and run with: mvn verify -Paws-it");
      }
    }
    return ConditionEvaluationResult.enabled("All required AWS environment variables are set.");
  }
}
