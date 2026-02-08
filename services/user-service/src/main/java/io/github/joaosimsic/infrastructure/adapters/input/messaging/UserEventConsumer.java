package io.github.joaosimsic.infrastructure.adapters.input.messaging;

import io.github.joaosimsic.core.events.UserCreatedEvent;
// import io.github.joaosimsic.core.ports.input.UserUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventConsumer {

  // private final UserUseCase userUseCase;

  @RabbitListener(queues = "user.created.queue")
  public void handleUserCreated(UserCreatedEvent event) {
    log.info(
        "Received UserCreatedEvent for user: {} with email: {}", event.userId(), event.email());

    try {
      // WIP
      log.info("Successfully processed event for user ID: {}", event.userId());
    } catch (Exception e) {
      // WIP
      log.error(
          "Error processing UserCreatedEvent for user {}: {}", event.userId(), e.getMessage());
      throw e;
    }
  }
}
