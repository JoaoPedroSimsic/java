package io.github.joaosimsic.infrastructure.adapters.input.messaging;

import io.github.joaosimsic.core.domain.User;
import io.github.joaosimsic.core.events.AuthUserEmailUpdatedEvent;
import io.github.joaosimsic.core.events.AuthUserRegisteredEvent;
import io.github.joaosimsic.core.ports.input.UserUseCase;
import io.github.joaosimsic.infrastructure.config.RabbitConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthEventConsumer {

  private final UserUseCase userUseCase;

  @RabbitListener(queues = RabbitConfig.AUTH_USER_REGISTERED_QUEUE)
  public void handleUserRegistered(AuthUserRegisteredEvent event) {
    log.info("Received AuthUserRegisteredEvent for user: {} with email: {}", 
        event.externalId(), event.email());

    try {
      User user = User.builder()
          .externalId(event.externalId())
          .email(event.email())
          .name(event.name())
          .build();

      userUseCase.createUser(user);
      log.info("Successfully created local user for external ID: {}", event.externalId());
    } catch (Exception e) {
      log.error("Error processing AuthUserRegisteredEvent for user {}: {}", 
          event.externalId(), e.getMessage());
      throw e;
    }
  }

  @RabbitListener(queues = RabbitConfig.AUTH_USER_EMAIL_UPDATED_QUEUE)
  public void handleUserEmailUpdated(AuthUserEmailUpdatedEvent event) {
    log.info("Received AuthUserEmailUpdatedEvent for user: {} with new email: {}",
        event.externalId(), event.newEmail());

    try {
      userUseCase.updateEmailByExternalId(event.externalId(), event.newEmail());
      log.info("Successfully updated email for user with external ID: {}", event.externalId());
    } catch (Exception e) {
      log.error("Error processing AuthUserEmailUpdatedEvent for user {}: {}",
          event.externalId(), e.getMessage());
      throw e;
    }
  }
}
