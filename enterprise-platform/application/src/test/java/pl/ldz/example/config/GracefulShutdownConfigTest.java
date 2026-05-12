package pl.ldz.example.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.env.ConfigurableEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("GracefulShutdownConfig")
class GracefulShutdownConfigTest {

  private final GracefulShutdownConfig config = new GracefulShutdownConfig();

  // ---------------------------------------------------------------------------
  // onApplicationReady
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("onApplicationReady returns a non-null listener")
  void onApplicationReady_returnsListener() {
    assertThat(config.onApplicationReady()).isNotNull();
  }

  @Test
  @DisplayName("onApplicationReady listener logs context id and port without throwing")
  void onApplicationReady_doesNotThrow() {
    ConfigurableEnvironment env = mock(ConfigurableEnvironment.class);
    when(env.getProperty("server.port", "8080")).thenReturn("8080");

    ConfigurableApplicationContext ctx = mock(ConfigurableApplicationContext.class);
    when(ctx.getId()).thenReturn("test-context");
    when(ctx.getEnvironment()).thenReturn(env);

    ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
    when(event.getApplicationContext()).thenReturn(ctx);

    ApplicationListener<ApplicationReadyEvent> listener = config.onApplicationReady();
    assertThatCode(() -> listener.onApplicationEvent(event)).doesNotThrowAnyException();
  }

  // ---------------------------------------------------------------------------
  // onContextClosed
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("onContextClosed returns a non-null listener")
  void onContextClosed_returnsListener() {
    assertThat(config.onContextClosed()).isNotNull();
  }

  @Test
  @DisplayName("onContextClosed listener logs context id without throwing")
  void onContextClosed_doesNotThrow() {
    ConfigurableApplicationContext ctx = mock(ConfigurableApplicationContext.class);
    when(ctx.getId()).thenReturn("test-context");

    ContextClosedEvent event = mock(ContextClosedEvent.class);
    when(event.getApplicationContext()).thenReturn(ctx);

    ApplicationListener<ContextClosedEvent> listener = config.onContextClosed();
    assertThatCode(() -> listener.onApplicationEvent(event)).doesNotThrowAnyException();
  }

  // ---------------------------------------------------------------------------
  // onShutdown
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("onShutdown logs shutdown message without throwing")
  void onShutdown_doesNotThrow() {
    assertThatCode(() -> config.onShutdown()).doesNotThrowAnyException();
  }
}
