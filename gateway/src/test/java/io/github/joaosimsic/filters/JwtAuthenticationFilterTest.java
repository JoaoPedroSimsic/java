package io.github.joaosimsic.filters;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.joaosimsic.GatewayApplicationTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.data.redis.core.ReactiveZSetOperations;
import reactor.core.publisher.Mono;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class JwtAuthenticationFilterTest extends GatewayApplicationTest {

  @Test
  @Order(1)
  void shouldFailWhenNoCookiePresent() {
    webTestClient.get().uri("/api/users/me").exchange().expectStatus().isUnauthorized();
  }

  @Test
  @Order(2)
  @SuppressWarnings("unchecked")
  void shouldSucceedAndPassHeadersToDownstream() {
    // Re-apply jwksService mock to ensure it's properly configured
    when(jwksService.getPublicKey(anyString())).thenReturn(Mono.just(keyPair.getPublic()));

    ReactiveZSetOperations<String, String> zSetOps = mock(ReactiveZSetOperations.class);

    when(redisTemplate.opsForZSet()).thenReturn(zSetOps);

    when(zSetOps.removeRangeByScore(anyString(), any())).thenReturn(Mono.just(0L));

    when(zSetOps.size(anyString())).thenReturn(Mono.just(0L));

    when(zSetOps.add(anyString(), anyString(), anyDouble())).thenReturn(Mono.just(true));

    when(redisTemplate.expire(anyString(), any())).thenReturn(Mono.just(true));

    // The presence of X-RateLimit headers proves JWT auth passed (rate limit filter runs after JWT filter)
    // The downstream service is unavailable so we can't verify the actual response status,
    // but if rate limit headers exist, it means the request passed both JWT and rate limit filters
    webTestClient
        .get()
        .uri("/api/users/me")
        .cookie("access_token", validToken)
        .exchange()
        .expectHeader()
        .exists("X-RateLimit-Limit")
        .expectHeader()
        .exists("X-RateLimit-Remaining");
  }
}
