package com.example.message.infrastructure.adapters.input.web.advice;

import com.example.message.core.exceptions.abstracts.InfrastructureException;
import com.example.message.core.exceptions.infrastructure.DatabaseUnavailableException;
import com.example.message.core.exceptions.infrastructure.MessagingException;
import com.example.message.infrastructure.adapters.input.web.responses.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class InfrastructureExceptionAdvice {

  @ExceptionHandler(DatabaseUnavailableException.class)
  public ResponseEntity<ErrorResponse> handleDatabaseUnavailable(
      DatabaseUnavailableException ex, HttpServletRequest request) {
    HttpHeaders headers = new HttpHeaders();
    headers.add("Retry-After", "30");

    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .headers(headers)
        .body(
            ErrorResponse.of(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "Database Unavailable",
                "The database is temporarily unavailable. Please try again later.",
                request.getRequestURI()));
  }

  @ExceptionHandler(InfrastructureException.class)
  public ResponseEntity<ErrorResponse> handleInfrastructureException(
      InfrastructureException ex, HttpServletRequest request) {
    return buildResponse(
        HttpStatus.INTERNAL_SERVER_ERROR, "Infrastructure Error", ex.getMessage(), request);
  }

  @ExceptionHandler(MessagingException.class)
  public ResponseEntity<ErrorResponse> handleMessagingException(
      MessagingException ex, HttpServletRequest request) {
    return buildResponse(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Messaging Failure",
        "The message could not be sent to the broker. The operation was aborted.",
        request);
  }

  private ResponseEntity<ErrorResponse> buildResponse(
      HttpStatus status, String error, String message, HttpServletRequest request) {
    ErrorResponse response =
        ErrorResponse.of(status.value(), error, message, request.getRequestURI());

    return new ResponseEntity<>(response, status);
  }
}
