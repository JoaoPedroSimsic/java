package io.github.joaosimsic.infrastructure.config;

import io.github.joaosimsic.core.exceptions.messaging.NonRetryableException;
import io.github.joaosimsic.events.auth.EmailUpdatedEvent;
import io.github.joaosimsic.events.auth.UserRegisteredEvent;
import java.util.HashMap;
import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

@Configuration
public class RabbitConfig {
  public static final String USER_EXCHANGE = "user.exchange";
  public static final String AUTH_EXCHANGE = "auth.exchange";

  public static final String DLX_EXCHANGE = "user-service.dlx";
  public static final String DLQ_QUEUE = "user-service.dlq";
  public static final String DLQ_ROUTING_KEY = "user-service.dead-letter";

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
  DirectExchange deadLetterExchange() {
    return new DirectExchange(DLX_EXCHANGE);
  }

  @Bean
  Queue deadLetterQueue() {
    return QueueBuilder.durable(DLQ_QUEUE).build();
  }

  @Bean
  Binding deadLetterBinding(DirectExchange deadLetterExchange, Queue deadLetterQueue) {
    return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(DLQ_ROUTING_KEY);
  }

  @Bean
  TopicExchange userExchange() {
    return new TopicExchange(USER_EXCHANGE);
  }

  @Bean
  Queue userCreatedQueue() {
    return QueueBuilder.durable(USER_CREATED_QUEUE)
        .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
        .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
        .build();
  }

  @Bean
  Binding userCreatedBinding(TopicExchange userExchange, Queue userCreatedQueue) {
    return BindingBuilder.bind(userCreatedQueue).to(userExchange).with(USER_CREATED_ROUTING_KEY);
  }

  @Bean
  Queue userUpdatedQueue() {
    return QueueBuilder.durable(USER_UPDATED_QUEUE)
        .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
        .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
        .build();
  }

  @Bean
  Binding userUpdatedBinding(TopicExchange userExchange, Queue userUpdatedQueue) {
    return BindingBuilder.bind(userUpdatedQueue).to(userExchange).with(USER_UPDATED_ROUTING_KEY);
  }

  @Bean
  Queue userDeletedQueue() {
    return QueueBuilder.durable(USER_DELETED_QUEUE)
        .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
        .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
        .build();
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
    return QueueBuilder.durable(AUTH_USER_REGISTERED_QUEUE)
        .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
        .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
        .build();
  }

  @Bean
  Binding authUserRegisteredBinding(TopicExchange authExchange, Queue authUserRegisteredQueue) {
    return BindingBuilder.bind(authUserRegisteredQueue)
        .to(authExchange)
        .with(AUTH_USER_REGISTERED_ROUTING_KEY);
  }

  @Bean
  Queue authUserEmailUpdatedQueue() {
    return QueueBuilder.durable(AUTH_USER_EMAIL_UPDATED_QUEUE)
        .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
        .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
        .build();
  }

  @Bean
  Binding authUserEmailUpdatedBinding(TopicExchange authExchange, Queue authUserEmailUpdatedQueue) {
    return BindingBuilder.bind(authUserEmailUpdatedQueue)
        .to(authExchange)
        .with(AUTH_USER_EMAIL_UPDATED_ROUTING_KEY);
  }

  @Bean
  Jackson2JsonMessageConverter messageConverter() {
    Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();

    DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();

    Map<String, Class<?>> idClassMapping = new HashMap<>();
    idClassMapping.put(
        "io.github.joaosimsic.core.events.UserRegisteredEvent", UserRegisteredEvent.class);

    idClassMapping.put(
        "io.github.joaosimsic.core.events.UserEmailUpdatedEvent", EmailUpdatedEvent.class);

    typeMapper.setIdClassMapping(idClassMapping);

    typeMapper.setTrustedPackages("io.github.joaosimsic.core.events");

    converter.setJavaTypeMapper(typeMapper);

    return converter;
  }

  @Bean
  RetryTemplate rabbitRetryTemplate() {
    RetryTemplate retryTemplate = new RetryTemplate();

    Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
    retryableExceptions.put(NonRetryableException.class, false);
    retryableExceptions.put(Exception.class, true);

    SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(3, retryableExceptions, true);
    retryTemplate.setRetryPolicy(retryPolicy);

    ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
    backOffPolicy.setInitialInterval(1000);
    backOffPolicy.setMultiplier(2.0);
    backOffPolicy.setMaxInterval(10000);
    retryTemplate.setBackOffPolicy(backOffPolicy);

    return retryTemplate;
  }

  @Bean
  MessageRecoverer messageRecoverer() {
    return new RejectAndDontRequeueRecoverer();
  }

  @Bean
  SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
      ConnectionFactory connectionFactory,
      Jackson2JsonMessageConverter messageConverter,
      RetryTemplate rabbitRetryTemplate,
      MessageRecoverer messageRecoverer) {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();

    factory.setConnectionFactory(connectionFactory);
    factory.setMessageConverter(messageConverter);
    factory.setDefaultRequeueRejected(false);
    factory.setAdviceChain(
        org.springframework.amqp.rabbit.config.RetryInterceptorBuilder.stateless()
            .retryOperations(rabbitRetryTemplate)
            .recoverer(messageRecoverer)
            .build());

    return factory;
  }
}
