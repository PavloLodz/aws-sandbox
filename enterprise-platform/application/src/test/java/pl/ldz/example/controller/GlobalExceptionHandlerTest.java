package pl.ldz.example.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  // ---------------------------------------------------------------------------
  // NoSuchElementException -> 404
  // ---------------------------------------------------------------------------
  @Test
  @DisplayName("handleNotFound returns 404 ProblemDetail with exception message")
  void handleNotFound_returns404() {
    NoSuchElementException ex = new NoSuchElementException("Item not found: 42");

    ProblemDetail result = handler.handleNotFound(ex);

    assertThat(result.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    assertThat(result.getDetail()).isEqualTo("Item not found: 42");
  }

  // ---------------------------------------------------------------------------
  // MethodArgumentNotValidException -> 400
  // ---------------------------------------------------------------------------
  @Test
  @DisplayName("handleValidation returns 400 ProblemDetail with field errors joined")
  void handleValidation_returns400() throws Exception {
    BeanPropertyBindingResult bindingResult =
        new BeanPropertyBindingResult(new Object(), "itemRequest");
    bindingResult.addError(new FieldError("itemRequest", "name", "must not be blank"));
    bindingResult.addError(new FieldError("itemRequest", "description", "size must be between 0 and 1000"));

    MethodArgumentNotValidException ex =
        new MethodArgumentNotValidException(null, bindingResult);

    ProblemDetail result = handler.handleValidation(ex);

    assertThat(result.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(result.getTitle()).isEqualTo("Validation failed");
    assertThat(result.getDetail())
        .contains("name: must not be blank")
        .contains("description: size must be between 0 and 1000");
  }

  @Test
  @DisplayName("handleValidation with single field error returns correct detail")
  void handleValidation_singleError() throws Exception {
    BeanPropertyBindingResult bindingResult =
        new BeanPropertyBindingResult(new Object(), "itemRequest");
    bindingResult.addError(new FieldError("itemRequest", "name", "must not be blank"));

    MethodArgumentNotValidException ex =
        new MethodArgumentNotValidException(null, bindingResult);

    ProblemDetail result = handler.handleValidation(ex);

    assertThat(result.getDetail()).isEqualTo("name: must not be blank");
  }
}
