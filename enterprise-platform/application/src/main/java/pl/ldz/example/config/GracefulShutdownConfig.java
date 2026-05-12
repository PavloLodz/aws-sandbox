package pl.ldz.example.config;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.event.ApplicationStartingEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;

/**
 * Configures graceful shutdown behaviour for the application.
 *
 * <p>Spring Boot's built-in graceful shutdown (server.shutdown=graceful) handles
 * the Tomcat layer — it stops accepting new requests and waits for in-flight
 * requests to finish before the server shuts down. This class adds:
 *
 * <ul>
 *   <li>Startup and shutdown lifecycle logging so operators can see exactly
 *       when the application entered and left each phase.</li>
 *   <li>A {@link ContextClosedEvent} listener that logs when the Spring context
 *       begins closing, giving visibility into the shutdown sequence.</li>
 * </ul>
 *
 * <p>Shutdown sequence on SIGTERM:
 * <ol>
 *   <li>Kubernetes sets readiness to REFUSING_TRAFFIC (via Spring's probe support).</li>
 *   <li>Tomcat stops accepting new HTTP connections.</li>
 *   <li>In-flight requests complete (up to spring.lifecycle.timeout-per-shutdown-phase).</li>
 *   <li>Spring context closes — beans are destroyed in reverse dependency order.</li>
 *   <li>JVM exits.</li>
 * </ol>
 */
@Configuration
@Slf4j
public class GracefulShutdownConfig {

  /**
   * Logs when the application has fully started and is ready to serve traffic.
   * This fires after the embedded server is up and all beans are initialised.
   */
  @Bean
  public ApplicationListener<ApplicationReadyEvent> onApplicationReady() {
    return event -> log.info(
        "Application '{}' is ready to serve traffic on port {}",
        event.getApplicationContext().getId(),
        event.getApplicationContext()
            .getEnvironment()
            .getProperty("server.port", "8080")
    );
  }

  /**
   * Logs when the Spring context begins closing (triggered by SIGTERM or
   * a programmatic shutdown). This is the first event in the shutdown sequence
   * — useful for correlating shutdown logs with infrastructure events.
   */
  @Bean
  public ApplicationListener<ContextClosedEvent> onContextClosed() {
    return event -> log.info(
        "Spring context '{}' is closing — graceful shutdown initiated",
        event.getApplicationContext().getId()
    );
  }

  /**
   * Final JVM-level hook. Called after the Spring context has fully closed.
   * Logs the last line before the process exits so operators know the
   * shutdown completed cleanly rather than being killed by SIGKILL.
   *
   * <p>Note: {@code @PreDestroy} runs during context destruction, before
   * the JVM shutdown hooks registered by Spring Boot itself.
   */
  @PreDestroy
  public void onShutdown() {
    log.info("Application shutdown complete — JVM is exiting");
  }
}
