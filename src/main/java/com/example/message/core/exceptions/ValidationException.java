package com.example.message.core.exceptions;

import com.example.message.core.exceptions.abstracts.BusinessException;

public class ValidationException extends BusinessException {
  public ValidationException(String message) {
    super(message);
  }
}
