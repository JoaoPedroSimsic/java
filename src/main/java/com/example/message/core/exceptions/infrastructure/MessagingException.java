package com.example.message.core.exceptions.infrastructure;

import com.example.message.core.exceptions.abstracts.InfrastructureException;

public class MessagingException extends InfrastructureException {
  public MessagingException(String message) {
    super(message);
  }
}
