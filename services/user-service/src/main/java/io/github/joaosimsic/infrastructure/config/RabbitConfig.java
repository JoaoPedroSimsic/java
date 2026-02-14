package io.github.joaosimsic.infrastructure.config;

import io.github.joaosimsic.core.events.AuthUserEmailUpdatedEvent;
import io.github.joaosimsic.core.events.AuthUserRegisteredEvent;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitConfig {
  public static final String USER_EXCHANGE = "user.exchange";
  public static final String AUTH_EXCHANGE = "auth.exchange";

  public static final String USER_CREATED_QUEUE = "user.created.queue";
  public static final String USER_UPDATED_QUEUE = "user.updated.queue";
  public static final String USER_DELETED_QUEUE = "user.deleted.queue";
  public static final String AUTH_USER_REGISTERED_QUEUE = "auth.user.registered.queue";
  public static final String AUTH_USER_EMAIL_UPDATED_QUEUE = "auth.user.email.updated.queue";

  public static final String USER_CREATED_ROUTING_KEY = "user.created";
  public static final String USER_UPDATED_ROUTING_KEY = "user.updated";
  public static final String USER_DELETED_ROUTING_KEY = "user.deleted";
  public static final String AUTH_USER_REGISTERED_ROUTING_KEY = "auth.user.registered";
  public static final String AUTH_USER_EMAIL_UPDATED_ROUTING_KEY = "auth.user.email.updated";

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

  @Bean
  TopicExchange authExchange() {
    return new TopicExchange(AUTH_EXCHANGE);
  }

  @Bean
  Queue authUserRegisteredQueue() {
    return new Queue(AUTH_USER_REGISTERED_QUEUE);
  }

  @Bean
  Binding authUserRegisteredBinding(TopicExchange authExchange, Queue authUserRegisteredQueue) {
    return BindingBuilder.bind(authUserRegisteredQueue).to(authExchange).with(AUTH_USER_REGISTERED_ROUTING_KEY);
  }

  @Bean
  Queue authUserEmailUpdatedQueue() {
    return new Queue(AUTH_USER_EMAIL_UPDATED_QUEUE);
  }

  @Bean
  Binding authUserEmailUpdatedBinding(TopicExchange authExchange, Queue authUserEmailUpdatedQueue) {
    return BindingBuilder.bind(authUserEmailUpdatedQueue).to(authExchange).with(AUTH_USER_EMAIL_UPDATED_ROUTING_KEY);
  }

  @Bean
  Jackson2JsonMessageConverter messageConverter() {
    Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
    DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
    
    Map<String, Class<?>> idClassMapping = new HashMap<>();
    idClassMapping.put("io.github.joaosimsic.core.events.UserRegisteredEvent", AuthUserRegisteredEvent.class);
    idClassMapping.put("io.github.joaosimsic.core.events.UserEmailUpdatedEvent", AuthUserEmailUpdatedEvent.class);
    typeMapper.setIdClassMapping(idClassMapping);
    typeMapper.setTrustedPackages("io.github.joaosimsic.core.events");
    
    converter.setJavaTypeMapper(typeMapper);
    return converter;
  }

  @Bean
  SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
      ConnectionFactory connectionFactory, Jackson2JsonMessageConverter messageConverter) {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    factory.setMessageConverter(messageConverter);
    return factory;
  }
}
