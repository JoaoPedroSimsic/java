package com.example.message.core.exceptions.abstracts;

public abstract class InfrastructureException extends RuntimeException {
  public InfrastructureException(String message) {
    super(message);
  }
}
