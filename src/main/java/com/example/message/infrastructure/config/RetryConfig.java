package com.example.message.infrastructure.config;

import java.net.ConnectException;
import java.util.HashMap;
import java.sql.SQLException;
import java.util.Map;
import org.springframework.retry.policy.SimpleRetryPolicy;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.support.RetryTemplate;

@Configuration
public class RetryConfig {

  @Bean
  public RetryTemplate retryTemplate() {
    RetryTemplate retryTemplate = new RetryTemplate();

    ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();

    backOffPolicy.setInitialInterval(1000);
    backOffPolicy.setMultiplier(2.0);
    backOffPolicy.setMaxInterval(10000);

    Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
    retryableExceptions.put(SQLException.class, true);
    retryableExceptions.put(TransientDataAccessException.class, true);
    retryableExceptions.put(RecoverableDataAccessException.class, true);
    retryableExceptions.put(ConnectException.class, true);

    SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(3, retryableExceptions);

    retryTemplate.setRetryPolicy(retryPolicy);
    retryTemplate.setBackOffPolicy(backOffPolicy);

    return retryTemplate;
  }
}
