package pl.ldz.example;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link ApplicationContextInitializer} that reads AWS environment variables
 * and injects them as concrete Spring property values before the context refreshes.
 *
 * <p>This solves the problem of unresolved placeholders like
 * {@code jdbc:postgresql://${RDS_HOSTNAME}:${RDS_PORT}/${RDS_DB_NAME}}:
 * by the time Flyway or HikariCP reads the datasource URL, the real values
 * from the environment are already in the property source.
 *
 * <p>Also activates the {@code aws} Spring profile so
 * {@code application-aws.properties} is loaded automatically.
 */
public class AwsPropertiesInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  @Override
  public void initialize(ConfigurableApplicationContext context) {
    // Activate the aws profile so application-aws.properties is loaded
    context.getEnvironment().addActiveProfile("aws");

    // Read env vars and publish them as a high-priority property source
    Map<String, Object> awsProps = new HashMap<>();
    addIfPresent(awsProps, "RDS_HOSTNAME");
    addIfPresent(awsProps, "RDS_PORT");
    addIfPresent(awsProps, "RDS_DB_NAME");
    addIfPresent(awsProps, "RDS_USERNAME");
    addIfPresent(awsProps, "RDS_PASSWORD");
    addIfPresent(awsProps, "S3_BUCKET");
    addIfPresent(awsProps, "S3_REGION");

    context.getEnvironment().getPropertySources()
        .addFirst(new MapPropertySource("awsTestProperties", awsProps));
  }

  private void addIfPresent(Map<String, Object> map, String envVar) {
    String value = System.getenv(envVar);
    if (value != null && !value.isBlank()) {
      map.put(envVar, value);
    }
  }
}
