package com.example.message.core.exceptions.infrastructure;

import com.example.message.core.exceptions.abstracts.InfrastructureException;

public class DatabaseUnavailableException extends InfrastructureException {
  public DatabaseUnavailableException(String message) {
    super(message);
  }
}
