package io.github.joaosimsic.core.exceptions.business;

import io.github.joaosimsic.core.exceptions.abstracts.BusinessException;

public class UserAlreadyExistsException extends BusinessException {
  public UserAlreadyExistsException(String message) {
    super(message);
  }
}
