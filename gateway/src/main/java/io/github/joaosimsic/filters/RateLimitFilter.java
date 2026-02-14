package io.github.joaosimsic.filters;

import io.github.joaosimsic.config.GatewayProperties;
import io.github.joaosimsic.config.GatewayProperties.RateDetails;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
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

  private static final String KEY_PREFIX = "rate_limit:";
  private static final Duration WINDOW = Duration.ofSeconds(1);

  @Override
  public int getOrder() {
    return -50;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    boolean authenticated = Boolean.TRUE.equals(exchange.getAttribute("authenticated"));

    RateDetails config =
        authenticated ? props.rateLimit().authenticated() : props.rateLimit().unauthenticated();

    String key = resolveKey(exchange, authenticated);

    return checkRateLimit(key, config)
        .flatMap(
            result -> {
              applyHeaders(exchange, result, config);

              if (!result.isAllowed()) {
                log.warn("Rate limit exceeded: {}", key);

                exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);

                return exchange.getResponse().setComplete();
              }

              return chain.filter(exchange);
            });
  }

  private Mono<RateLimitResult> checkRateLimit(String key, RateDetails config) {
    long now = Instant.now().toEpochMilli();

    long windowStart = now - WINDOW.toMillis();

    return redisTemplate
        .opsForZSet()
        .removeRangeByScore(key, Range.closed(0.0, (double) windowStart))
        .then(redisTemplate.opsForZSet().size(key))
        .flatMap(count -> handleRequest(key, count != null ? count : 0, config, now))
        .onErrorResume(
            e -> {
              log.error("Rate limit check failed for {}: {}", key, e.getMessage());
              return Mono.just(new RateLimitResult(true, config.burstCapacity()));
            });
  }

  private Mono<RateLimitResult> handleRequest(
      String key, long currentCount, RateDetails config, long now) {
    boolean allowed = currentCount < config.burstCapacity();

    int remaining = (int) Math.max(0, config.burstCapacity() - (currentCount + 1));

    if (!allowed) {
      return Mono.just(new RateLimitResult(false, 0));
    }

    String member = now + ":" + UUID.randomUUID();

    return redisTemplate
        .opsForZSet()
        .add(key, member, now)
        .then(redisTemplate.expire(key, WINDOW.multipliedBy(2)))
        .thenReturn(new RateLimitResult(true, remaining));
  }

  private String resolveKey(ServerWebExchange exchange, boolean authenticated) {
    if (authenticated) {
      String email = exchange.getAttribute("userEmail");

      if (email != null) return KEY_PREFIX + "user:" + email;
    }

    String ip =
        exchange.getRequest().getRemoteAddress() != null
            ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
            : "unknown";

    return KEY_PREFIX + "ip:" + ip;
  }

  private void applyHeaders(ServerWebExchange exchange, RateLimitResult res, RateDetails config) {
    var headers = exchange.getResponse().getHeaders();

    headers.add("X-RateLimit-Limit", String.valueOf(config.burstCapacity()));
    headers.add("X-RateLimit-Remaining", String.valueOf(res.remaining()));
    headers.add("X-RateLimit-Reset", String.valueOf(WINDOW.toSeconds()));
  }

  private record RateLimitResult(boolean isAllowed, int remaining) {}
}
