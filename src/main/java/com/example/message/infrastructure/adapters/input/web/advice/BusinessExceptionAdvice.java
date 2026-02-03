package com.example.message.infrastructure.adapters.input.web.advice;

import com.example.message.core.exceptions.business.*;
import com.example.message.core.exceptions.abstracts.BusinessException;
import com.example.message.infrastructure.adapters.input.web.responses.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class BusinessExceptionAdvice {

  @ExceptionHandler(UserNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(
      UserNotFoundException ex, HttpServletRequest request) {
    return buildResponse(HttpStatus.NOT_FOUND, "Resource Not Found", ex.getMessage(), request);
  }

  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<ErrorResponse> handleConflict(
      ConflictException ex, HttpServletRequest request) {
    return buildResponse(HttpStatus.CONFLICT, "Data Conflict", ex.getMessage(), request);
  }

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ErrorResponse> handleBusinessException(
      BusinessException ex, HttpServletRequest request) {
    return buildResponse(
        HttpStatus.UNPROCESSABLE_CONTENT, "Business Rule Violation", ex.getMessage(), request);
  }

  @ExceptionHandler(InvalidCredentialsException.class)
  public ResponseEntity<ErrorResponse> handleInvalidCredentials(
      InvalidCredentialsException ex, HttpServletRequest request) {
    return buildResponse(HttpStatus.UNAUTHORIZED, "Invalid Credentials", ex.getMessage(), request);
  }

  private ResponseEntity<ErrorResponse> buildResponse(
      HttpStatus status, String error, String message, HttpServletRequest request) {
    ErrorResponse response =
        ErrorResponse.of(status.value(), error, message, request.getRequestURI());

    return new ResponseEntity<>(response, status);
  }
}
