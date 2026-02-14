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

    when(jwksService.getPublicKey(anyString())).thenReturn(Mono.just(keyPair.getPublic()));

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
        .expectStatus()
        .value(
            status -> {
              if (status == 401) {
                throw new AssertionError("Expected JWT authentication to pass, but got 401");
              }
            });
  }
}
