package io.github.joaosimsic.core.exceptions.business;

import io.github.joaosimsic.core.exceptions.abstracts.BusinessException;

public class InvalidCredentialsException extends BusinessException {
  public InvalidCredentialsException(String message) {
    super(message);
  }
}
