package io.github.joaosimsic.core.exceptions.abstracts;

public abstract class BusinessException extends RuntimeException {
  protected BusinessException(String message) {
    super(message);
  }

  protected BusinessException(String message, Throwable cause) {
    super(message, cause);
  }
}
