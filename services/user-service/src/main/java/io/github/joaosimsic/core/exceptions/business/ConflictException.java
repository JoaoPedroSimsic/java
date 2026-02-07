package io.github.joaosimsic.core.exceptions.business;

import io.github.joaosimsic.core.exceptions.abstracts.BusinessException;

public class ConflictException extends BusinessException {
  public ConflictException(String message) {
    super(message);
  }
}
