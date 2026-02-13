package io.github.joaosimsic.core.exceptions.business;

import io.github.joaosimsic.core.exceptions.abstracts.BusinessException;

public class AuthenticationException extends BusinessException {
  public AuthenticationException(String message) {
    super(message);
  }

  public AuthenticationException(String message, Throwable cause) {
    super(message, cause);
  }
}
