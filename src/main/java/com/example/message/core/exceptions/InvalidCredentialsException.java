package com.example.message.core.exceptions;

import com.example.message.core.exceptions.abstracts.BusinessException;

public class InvalidCredentialsException extends BusinessException {
  public InvalidCredentialsException(String message) {
    super(message);
  }
}
