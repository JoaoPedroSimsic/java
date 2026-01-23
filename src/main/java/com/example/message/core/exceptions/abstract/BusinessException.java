package com.example.message.core.exceptions;

public abstract class BusinessException extends RuntimeException {
  public BusinessException(String message) {
    super(message);
  }
}
