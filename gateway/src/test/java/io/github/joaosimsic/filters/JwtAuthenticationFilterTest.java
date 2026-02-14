package io.github.joaosimsic.filters;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.joaosimsic.GatewayApplicationTest;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.util.Date;
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
        .expectHeader()
        .exists("X-RateLimit-Limit")
        .expectHeader()
        .exists("X-RateLimit-Remaining");
  }

  @Test
  void shouldRejectExpiredToken() {
    when(jwksService.getPublicKey(anyString())).thenReturn(Mono.just(keyPair.getPublic()));

    String expiredToken =
        Jwts.builder()
            .setSubject("user-123")
            .claim("email", TEST_EMAIL)
            .setHeaderParam("kid", "test-key-id")
            .setIssuer("test-issuer")
            .setIssuedAt(new Date(System.currentTimeMillis() - 7200000)) 
            .setExpiration(new Date(System.currentTimeMillis() - 3600000)) 
            .signWith(keyPair.getPrivate(), SignatureAlgorithm.RS256)
            .compact();

    webTestClient
        .get()
        .uri("/api/users/me")
        .cookie("access_token", expiredToken)
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void shouldRejectTokenWithInvalidIssuer() {
    when(jwksService.getPublicKey(anyString())).thenReturn(Mono.just(keyPair.getPublic()));

    String wrongIssuerToken =
        Jwts.builder()
            .setSubject("user-123")
            .claim("email", TEST_EMAIL)
            .setHeaderParam("kid", "test-key-id")
            .setIssuer("wrong-issuer") 
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + 3600000))
            .signWith(keyPair.getPrivate(), SignatureAlgorithm.RS256)
            .compact();

    webTestClient
        .get()
        .uri("/api/users/me")
        .cookie("access_token", wrongIssuerToken)
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void shouldRejectMalformedToken() {
    webTestClient
        .get()
        .uri("/api/users/me")
        .cookie("access_token", "invalid-token-string")
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void shouldRejectTokenWithMissingKid() {
    when(jwksService.getPublicKey(anyString())).thenReturn(Mono.just(keyPair.getPublic()));

    String tokenWithoutKid =
        Jwts.builder()
            .setSubject("user-123")
            .claim("email", TEST_EMAIL)
            .setIssuer("test-issuer")
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + 3600000))
            .signWith(keyPair.getPrivate(), SignatureAlgorithm.RS256)
            .compact();

    webTestClient
        .get()
        .uri("/api/users/me")
        .cookie("access_token", tokenWithoutKid)
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void shouldRejectTokenWhenKidNotFoundInJwks() {
    when(jwksService.getPublicKey(anyString()))
        .thenReturn(Mono.error(new RuntimeException("Key not found in JWKS")));

    webTestClient
        .get()
        .uri("/api/users/me")
        .cookie("access_token", validToken)
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void shouldBypassAuthForPublicAuthPath() {
    webTestClient
        .get()
        .uri("/api/auth/login")
        .exchange()
        .expectStatus()
        .value(
            status -> {
              if (status == 401) {
                throw new AssertionError("Public path /api/auth/login should bypass JWT auth");
              }
            });
  }

  @Test
  void shouldBypassAuthForSwaggerPath() {
    webTestClient
        .get()
        .uri("/swagger-ui/index.html")
        .exchange()
        .expectStatus()
        .value(
            status -> {
              if (status == 401) {
                throw new AssertionError("Public path /swagger-ui should bypass JWT auth");
              }
            });
  }

  @Test
  void shouldBypassAuthForActuatorHealthPath() {
    webTestClient
        .get()
        .uri("/actuator/health")
        .exchange()
        .expectStatus()
        .value(
            status -> {
              if (status == 401) {
                throw new AssertionError("Public path /actuator/health should bypass JWT auth");
              }
            });
  }

  @Test
  void shouldBypassAuthForApiDocsPath() {
    webTestClient
        .get()
        .uri("/v3/api-docs")
        .exchange()
        .expectStatus()
        .value(
            status -> {
              if (status == 401) {
                throw new AssertionError("Public path /v3/api-docs should bypass JWT auth");
              }
            });
  }
}
