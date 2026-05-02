package pl.ldz.example;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import static org.junit.jupiter.api.Assertions.fail;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

/**
 * Base class for LOCAL integration tests.
 *
 * <p>Starts two containers:
 * <ul>
 *   <li>{@link PostgreSQLContainer} — replaces the real RDS database</li>
 *   <li>{@link LocalStackContainer} — replaces the real AWS S3 bucket</li>
 * </ul>
 *
 * <p>No AWS credentials or real AWS services are needed.
 * Runs fully offline with Docker on the developer's machine.
 *
 * <p>Spring profile {@code test-local} is activated so
 * {@code application-test-local.properties} is loaded as a base,
 * then all dynamic values (URLs, credentials) are injected via
 * {@link DynamicPropertySource} before the application context starts.
 */
@Testcontainers(disabledWithoutDocker = false)
@TestPropertySource(properties = {
    "spring.profiles.active=test-local",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration"
})
public abstract class AbstractLocalIT {

  private static final String TEST_BUCKET = "test-bucket";

  @Container
  protected static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("aws_attempt_test")
          .withUsername("test")
          .withPassword("test");

  @Container
  protected static final LocalStackContainer LOCALSTACK =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:3"))
          .withServices(S3);

  /**
   * Fails the build immediately when Docker is unavailable,
   * rather than silently skipping all tests.
   */
  @BeforeAll
  static void requireDocker() {
    if (!DockerClientFactory.instance().isDockerAvailable()) {
      fail("Docker is not available — make sure Docker is running before executing integration tests");
    }
  }

  /**
   * Creates the S3 test bucket in LocalStack once containers are up.
   * Must run before the Spring context starts (hence static + BeforeAll),
   * but after Testcontainers has started the LocalStack container.
   */
  @BeforeAll
  static void createS3Bucket() {
    S3Client s3 = S3Client.builder()
        .region(Region.of(LOCALSTACK.getRegion()))
        .endpointOverride(LOCALSTACK.getEndpointOverride(S3))
        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
        .credentialsProvider(StaticCredentialsProvider.create(
            AwsBasicCredentials.create(LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey())))
        .build();

    s3.createBucket(CreateBucketRequest.builder().bucket(TEST_BUCKET).build());
    s3.close();
  }

  /**
   * Injects container-assigned connection details into the Spring context
   * before it is refreshed.
   */
  @DynamicPropertySource
  static void overrideProperties(DynamicPropertyRegistry registry) {
    // PostgreSQL → replaces RDS
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.flyway.enabled", () -> "true");

    // LocalStack → replaces S3
    registry.add("aws.s3.region", LOCALSTACK::getRegion);
    registry.add("aws.s3.bucket", () -> TEST_BUCKET);
    registry.add("aws.s3.endpoint-override",
        () -> LOCALSTACK.getEndpointOverride(S3).toString());
    registry.add("aws.s3.access-key", LOCALSTACK::getAccessKey);
    registry.add("aws.s3.secret-key", LOCALSTACK::getSecretKey);
  }
}
