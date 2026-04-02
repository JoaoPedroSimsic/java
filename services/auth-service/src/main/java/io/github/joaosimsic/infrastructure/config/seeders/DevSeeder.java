package io.github.joaosimsic.infrastructure.config.seeders;

import io.github.joaosimsic.core.exceptions.business.UserAlreadyExistsException;
import io.github.joaosimsic.core.ports.input.AuthUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class DevSeeder implements ApplicationRunner {
  private final AuthUseCase authUseCase;

  @Override
  public void run(ApplicationArguments args) {
    String email = "dev@hermes.local";

    try {
      authUseCase.register("Dev", email, "password123");

      log.info("Seeded dev user: {}", email);
    } catch (UserAlreadyExistsException e) {
      log.debug("Dev user already exists, skipping seeding");
    }
  }
}
