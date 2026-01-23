package com.example.message.core.exceptions;

import com.example.message.core.exceptions.abstracts.BusinessException;

public class ConflictException extends BusinessException {
  public ConflictException(String message) {
    super(message);
  }
}
