package io.github.joaosimsic.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimiterConfig {

  @Bean
  @Primary
  KeyResolver userKeyResolver() {
    return exchange -> {
      String userEmail = exchange.getAttribute("userEmail");
      if (userEmail != null) {
        return Mono.just("user:" + userEmail);
      }
      String ip =
          exchange.getRequest().getRemoteAddress() != null
              ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
              : "unknown";
      return Mono.just("ip:" + ip);
    };
  }

  @Bean("ipKeyResolver")
  KeyResolver ipKeyResolver() {
    return exchange -> {
      String ip =
          exchange.getRequest().getRemoteAddress() != null
              ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
              : "unknown";
      return Mono.just("ip:" + ip);
    };
  }

  @Bean
  ReactiveRedisTemplate<String, String> rateLimitRedisTemplate(
      ReactiveRedisConnectionFactory connectionFactory) {
    StringRedisSerializer serializer = new StringRedisSerializer();
    RedisSerializationContext<String, String> context =
        RedisSerializationContext.<String, String>newSerializationContext(serializer)
            .key(serializer)
            .value(serializer)
            .hashKey(serializer)
            .hashValue(serializer)
            .build();
    return new ReactiveRedisTemplate<>(connectionFactory, context);
  }
}
