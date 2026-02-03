package com.example.message.core.exceptions.infrastructure;

import com.example.message.core.exceptions.abstracts.BusinessException;

public class DatabaseUnavailableException extends BusinessException {
  public DatabaseUnavailableException(String message) {
    super(message);
  }
}
