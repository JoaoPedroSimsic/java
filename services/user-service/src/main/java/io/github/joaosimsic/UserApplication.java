package io.github.joaosimsic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
@ConfigurationPropertiesScan("io.github.joaosimsic.infrastructure.config.properties")
public class UserApplication {

  public static void main(String[] args) {
    SpringApplication.run(UserApplication.class, args);
  }
}
