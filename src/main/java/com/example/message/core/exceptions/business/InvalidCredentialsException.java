package com.example.message.core.exceptions.business;

import com.example.message.core.exceptions.abstracts.BusinessException;

public class InvalidCredentialsException extends BusinessException {
  public InvalidCredentialsException(String message) {
    super(message);
  }
}
