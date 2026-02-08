package io.github.joaosimsic.core.exceptions.infrastructure;

import io.github.joaosimsic.core.exceptions.abstracts.InfrastructureException;

public class MessagingException extends InfrastructureException {
  public MessagingException(String message) {
    super(message);
  }
}
