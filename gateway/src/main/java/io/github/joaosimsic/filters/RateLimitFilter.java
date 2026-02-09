package io.github.joaosimsic.filters;

import io.github.joaosimsic.config.GatewayProperties;
import io.github.joaosimsic.config.GatewayProperties.RateDetails;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter implements GlobalFilter, Ordered {

  private final ReactiveRedisTemplate<String, String> redisTemplate;
  private final GatewayProperties props;

  private static final String RATE_LIMIT_KEY_PREFIX = "rate_limit:";
  private static final Duration WINDOW_SIZE = Duration.ofSeconds(1);

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    Boolean authenticated = exchange.getAttribute("authenticated");
    boolean isAuthenticated = Boolean.TRUE.equals(authenticated);

    RateDetails config =
        isAuthenticated
            ? props.getRateLimit().getAuthenticated()
            : props.getRateLimit().getUnauthenticated();

    String key = buildRateLimitKey(exchange, isAuthenticated);

    return isAllowed(key, config)
        .flatMap(
            allowed -> {
              if (!allowed.isAllowed()) {
                log.warn("Rate limit exceeded for key: {}", key);
                exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                addRateLimitHeaders(exchange, allowed, config);
                return exchange.getResponse().setComplete();
              }

              addRateLimitHeaders(exchange, allowed, config);
              return chain.filter(exchange);
            });
  }

  private String buildRateLimitKey(ServerWebExchange exchange, boolean isAuthenticated) {
    if (isAuthenticated) {
      String userEmail = exchange.getAttribute("userEmail");
      if (userEmail != null) {
        return RATE_LIMIT_KEY_PREFIX + "user:" + userEmail;
      }
    }

    String ip =
        exchange.getRequest().getRemoteAddress() != null
            ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
            : "unknown";
    return RATE_LIMIT_KEY_PREFIX + "ip:" + ip;
  }

  private Mono<RateLimitResult> isAllowed(String key, RateDetails config) {
    long now = Instant.now().toEpochMilli();
    long windowStart = now - WINDOW_SIZE.toMillis();

    return redisTemplate
        .opsForZSet()
        .removeRangeByScore(key, Range.closed(0.0, (double) windowStart))
        .then(redisTemplate.opsForZSet().size(key))
        .flatMap(
            currentCount -> {
              long count = currentCount != null ? currentCount : 0;
              int remaining = Math.max(0, config.getBurstCapacity() - (int) count - 1);

              if (count >= config.getBurstCapacity()) {
                return Mono.just(new RateLimitResult(false, remaining, WINDOW_SIZE.toSeconds()));
              }

              String member = now + ":" + java.util.UUID.randomUUID();
              return redisTemplate
                  .opsForZSet()
                  .add(key, member, now)
                  .then(redisTemplate.expire(key, WINDOW_SIZE.multipliedBy(2)))
                  .thenReturn(new RateLimitResult(true, remaining, WINDOW_SIZE.toSeconds()));
            })
        .onErrorResume(
            e -> {
              log.error("Rate limit check failed for key {}: {}", key, e.getMessage());
              return Mono.just(
                  new RateLimitResult(true, config.getBurstCapacity(), WINDOW_SIZE.toSeconds()));
            });
  }

  private void addRateLimitHeaders(
      ServerWebExchange exchange, RateLimitResult result, RateDetails config) {
    exchange
        .getResponse()
        .getHeaders()
        .add("X-RateLimit-Limit", String.valueOf(config.getBurstCapacity()));
    exchange
        .getResponse()
        .getHeaders()
        .add("X-RateLimit-Remaining", String.valueOf(result.remaining()));
    exchange
        .getResponse()
        .getHeaders()
        .add("X-RateLimit-Reset", String.valueOf(result.resetInSeconds()));
  }

  @Override
  public int getOrder() {
    return -50;
  }

  private record RateLimitResult(boolean isAllowed, int remaining, long resetInSeconds) {}
}
