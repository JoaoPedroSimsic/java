package io.github.joaosimsic.infrastructure.adapters.input.messaging;

import io.github.joaosimsic.core.domain.User;
import io.github.joaosimsic.core.ports.input.UserUseCase;
import io.github.joaosimsic.events.auth.EmailUpdatedEvent;
import io.github.joaosimsic.events.auth.UserRegisteredEvent;
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
  public void handleUserRegistered(UserRegisteredEvent event) {
    log.info(
        "Received AuthUserRegisteredEvent for user: {} with email: {}",
        event.getExternalId(),
        event.getEmail());

    try {
      var user =
          User.builder()
              .externalId(event.getExternalId())
              .email(event.getEmail())
              .name(event.getName())
              .build();

      userUseCase.createUser(user);

      log.info("Successfully created local user for external ID: {}", event.getExternalId());
    } catch (Exception e) {
      log.error(
          "Error processing AuthUserRegisteredEvent for user {}: {}",
          event.getExternalId(),
          e.getMessage());

      throw e;
    }
  }

  @RabbitListener(queues = RabbitConfig.AUTH_USER_EMAIL_UPDATED_QUEUE)
  public void handleUserEmailUpdated(EmailUpdatedEvent event) {
    log.info(
        "Received AuthUserEmailUpdatedEvent for user: {} with new email: {}",
        event.getExternalId(),
        event.getNewEmail());

    try {
      userUseCase.updateEmailByExternalId(event.getExternalId(), event.getNewEmail());

      log.info("Successfully updated email for user with external ID: {}", event.getExternalId());
    } catch (Exception e) {
      log.error(
          "Error processing AuthUserEmailUpdatedEvent for user {}: {}",
          event.getExternalId(),
          e.getMessage());

      throw e;
    }
  }
}
