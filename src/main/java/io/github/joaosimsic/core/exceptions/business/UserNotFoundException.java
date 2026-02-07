package io.github.joaosimsic.core.exceptions.business;

import io.github.joaosimsic.core.exceptions.abstracts.BusinessException;

public class UserNotFoundException extends BusinessException {
  public UserNotFoundException(String message) {
    super(message);
  }
}
