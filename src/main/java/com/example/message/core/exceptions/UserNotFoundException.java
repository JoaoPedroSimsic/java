package com.example.message.core.exceptions;

public class UserNotFoundException extends BusinessException {
  public UserNotFoundException(String message) {
    super(message);
  }
}
