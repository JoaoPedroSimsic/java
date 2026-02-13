package io.github.joaosimsic.filters;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.joaosimsic.GatewayApplicationTest;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveZSetOperations;
import reactor.core.publisher.Mono;

public class RateLimitFilterTest extends GatewayApplicationTest {

  @Test
  @SuppressWarnings("unchecked")
  void shouldReturn429WhenRateLimitIsExceeded() {
    ReactiveZSetOperations<String, String> zSetOps = mock(ReactiveZSetOperations.class);

    when(redisTemplate.opsForZSet()).thenReturn(zSetOps);

    when(zSetOps.removeRangeByScore(anyString(), any())).thenReturn(Mono.just(0L));

    when(zSetOps.size(anyString())).thenReturn(Mono.just(11L));

    webTestClient
        .get()
        .uri("/api/auth/login")
        .exchange()
        .expectStatus()
        .isEqualTo(429)
        .expectHeader()
        .valueEquals("X-RateLimit-Remaining", "0");
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldAllowRequestWhenBucketHasSpace() {
    ReactiveZSetOperations<String, String> zSetOps = mock(ReactiveZSetOperations.class);
    when(redisTemplate.opsForZSet()).thenReturn(zSetOps);

    when(zSetOps.removeRangeByScore(anyString(), any())).thenReturn(Mono.just(0L));

    when(zSetOps.size(anyString())).thenReturn(Mono.just(2L));

    when(zSetOps.add(anyString(), anyString(), anyDouble())).thenReturn(Mono.just(true));

    when(redisTemplate.expire(anyString(), any())).thenReturn(Mono.just(true));

    // Verify rate limit passes (not 429) and remaining tokens are reported
    webTestClient
        .get()
        .uri("/api/auth/login")
        .exchange()
        .expectHeader()
        .exists("X-RateLimit-Remaining")
        .expectHeader()
        .valueEquals("X-RateLimit-Remaining", "7");
  }
}
