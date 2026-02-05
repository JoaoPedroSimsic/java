package com.example.message.infrastructure.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
  public static final String USER_EXCHANGE = "user.exchange";
  public static final String USER_CREATED_QUEUE = "user.created.queue";
  public static final String USER_CREATED_ROUTING_QUEUE = "user.created";

  @Bean
  public TopicExchange userExchange() {
    return new TopicExchange(USER_EXCHANGE);
  }

  @Bean
  public Queue userCreatedQueue() {
    return new Queue(USER_CREATED_QUEUE);
  }

  @Bean
  public Binding userCreatedBinding(TopicExchange userExchange, Queue userCreatedQueue) {
    return BindingBuilder.bind(userCreatedQueue).to(userExchange).with(USER_CREATED_ROUTING_QUEUE);
  }
}
