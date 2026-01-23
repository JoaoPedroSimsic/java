package com.example.message.core.exceptions;

import com.example.message.core.exceptions.abstracts.BusinessException;

public class UserNotFoundException extends BusinessException {
  public UserNotFoundException(String message) {
    super(message);
  }
}
