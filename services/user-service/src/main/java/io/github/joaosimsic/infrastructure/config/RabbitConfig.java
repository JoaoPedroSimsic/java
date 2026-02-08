package io.github.joaosimsic.infrastructure.config;

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

  public static final String USER_UPDATED_QUEUE = "user.updated.queue";

  public static final String USER_DELETED_QUEUE = "user.deleted.queue";

  public static final String USER_CREATED_ROUTING_KEY = "user.created";

  public static final String USER_UPDATED_ROUTING_KEY = "user.updated";

  public static final String USER_DELETED_ROUTING_KEY = "user.deleted";

  @Bean
  TopicExchange userExchange() {
    return new TopicExchange(USER_EXCHANGE);
  }

  @Bean
  Queue userCreatedQueue() {
    return new Queue(USER_CREATED_QUEUE);
  }

  @Bean
  Binding userCreatedBinding(TopicExchange userExchange, Queue userCreatedQueue) {
    return BindingBuilder.bind(userCreatedQueue).to(userExchange).with(USER_CREATED_ROUTING_KEY);
  }

  @Bean
  Queue userUpdatedQueue() {
    return new Queue(USER_UPDATED_QUEUE);
  }

  @Bean
  Binding userUpdatedBinding(TopicExchange userExchange, Queue userUpdatedQueue) {
    return BindingBuilder.bind(userUpdatedQueue).to(userExchange).with(USER_UPDATED_ROUTING_KEY);
  }

  @Bean
  Queue userDeletedQueue() {
    return new Queue(USER_DELETED_QUEUE);
  }

  @Bean
  Binding userDeletedBinding(TopicExchange userExchange, Queue userDeletedQueue) {
    return BindingBuilder.bind(userDeletedQueue).to(userExchange).with(USER_DELETED_ROUTING_KEY);
  }
}
