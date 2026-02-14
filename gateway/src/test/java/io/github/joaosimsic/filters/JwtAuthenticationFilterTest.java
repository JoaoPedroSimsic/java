package io.github.joaosimsic.filters;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.joaosimsic.GatewayApplicationTest;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveZSetOperations;
import reactor.core.publisher.Mono;

public class JwtAuthenticationFilterTest extends GatewayApplicationTest {

  @Test
  void shouldFailWhenNoCookiePresent() {
    webTestClient.get().uri("/api/users/me").exchange().expectStatus().isUnauthorized();
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldSucceedAndPassHeadersToDownstream() {
    ReactiveZSetOperations<String, String> zSetOps = mock(ReactiveZSetOperations.class);

    when(redisTemplate.opsForZSet()).thenReturn(zSetOps);

    when(zSetOps.removeRangeByScore(anyString(), any())).thenReturn(Mono.just(0L));

    when(zSetOps.size(anyString())).thenReturn(Mono.just(0L));

    when(zSetOps.add(anyString(), anyString(), anyDouble())).thenReturn(Mono.just(true));

    when(redisTemplate.expire(anyString(), any())).thenReturn(Mono.just(true));

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
