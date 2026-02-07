package io.github.joaosimsic.infrastructure.adapters.input.web.advice;

import io.github.joaosimsic.infrastructure.adapters.input.web.responses.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class WebExceptionAdvice {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    Map<String, String> details = new HashMap<>();

    ex.getBindingResult()
        .getFieldErrors()
        .forEach(err -> details.put(err.getField(), err.getDefaultMessage()));

    return ResponseEntity.badRequest()
        .body(
            new ErrorResponse(
                null,
                HttpStatus.BAD_REQUEST.value(),
                "Validation Failed",
                "Invalid input data",
                request.getRequestURI(),
                details));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleTypeMismatch(
      MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
    String message =
        String.format(
            "Parameter '%s' must be of type %s",
            ex.getName(), ex.getRequiredType().getSimpleName());

    return ResponseEntity.badRequest()
        .body(
            ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(), "Type Mismatch", message, request.getRequestURI()));
  }
}
