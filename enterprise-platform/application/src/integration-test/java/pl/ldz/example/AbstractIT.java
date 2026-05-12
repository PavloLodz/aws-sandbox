package pl.ldz.example;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Base class for integration tests.
 *
 * <p>Starts a single shared {@link PostgreSQLContainer} and wires its connection
 * properties into the Spring context via {@link DynamicPropertySource}.
 * Flyway is explicitly enabled so migrations always run against the fresh container,
 * regardless of which Spring profile is active.
 *
 * <p>Uses {@code disabledWithoutDocker = false} so a missing or broken Docker
 * installation is always a hard build failure, never a silent skip.
 */
@Testcontainers(disabledWithoutDocker = false)
@TestPropertySource(properties = {
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration"
})
public abstract class AbstractIT {

  @Container
  protected static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("aws_attempt_test")
          .withUsername("test")
          .withPassword("test");

  /**
   * Fails the entire test class when Docker is not reachable.
   * Ensures the build is RED in environments where Docker is unavailable,
   * rather than silently passing with 0 tests.
   */
  @BeforeAll
  static void requireDocker() {
    if (DockerClientFactory.instance() == null) {
      fail("DockerClientFactory.instance() == null — Docker is not available");
    }
    if (!DockerClientFactory.instance().isDockerAvailable()) {
      fail("Docker is not available — make sure Docker is running before executing integration tests");
    }
  }

  /**
   * Overrides Spring datasource properties with the Testcontainers-assigned values
   * before the application context is refreshed.
   */
  @DynamicPropertySource
  static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.flyway.enabled", () -> "true");
  }
}
