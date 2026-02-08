package io.github.joaosimsic.core.exceptions.abstracts;

public abstract class BusinessException extends RuntimeException {
  public BusinessException(String message) {
    super(message);
  }
}
