package io.github.joaosimsic.core.exceptions.business;

import io.github.joaosimsic.core.exceptions.abstracts.BusinessException;

public class ValidationException extends BusinessException {
  public ValidationException(String message) {
    super(message);
  }
}
