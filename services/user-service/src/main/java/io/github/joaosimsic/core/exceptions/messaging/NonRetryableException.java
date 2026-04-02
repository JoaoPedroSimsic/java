package io.github.joaosimsic.core.exceptions.messaging;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;

public class NonRetryableException extends AmqpRejectAndDontRequeueException {

  public NonRetryableException(String message) {
    super(message);
  }

  public NonRetryableException(String message, Throwable cause) {
    super(message, cause);
  }
}
