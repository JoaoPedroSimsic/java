package io.github.joaosimsic.core.exceptions.infrastructure;

import io.github.joaosimsic.core.exceptions.abstracts.InfrastructureException;

public class DatabaseUnavailableException extends InfrastructureException {
  public DatabaseUnavailableException(String message) {
    super(message);
  }
}
