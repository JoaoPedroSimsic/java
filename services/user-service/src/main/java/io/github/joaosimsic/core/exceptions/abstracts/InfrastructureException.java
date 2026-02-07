package io.github.joaosimsic.core.exceptions.abstracts;

public abstract class InfrastructureException extends RuntimeException {
  public InfrastructureException(String message) {
    super(message);
  }
}
