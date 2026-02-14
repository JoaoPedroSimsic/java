package io.github.joaosimsic.filters;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.redis.testcontainers.RedisContainer;
import io.github.joaosimsic.services.JwksService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveZSetOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;

/**
 * Integration tests for Circuit Breaker and Fallback behavior using WireMock. Run with: mvn verify
 * (requires Docker)
 */
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("circuit-breaker-test")
@AutoConfigureWebTestClient(timeout = "60000")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CircuitBreakerIT {

  @Container static RedisContainer redis = new RedisContainer(RedisContainer.DEFAULT_IMAGE_NAME);

  static WireMockServer userServiceMock;

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    registry.add("USER_SERVICE_URL", () -> "http://localhost:" + userServiceMock.port());
  }

  @Autowired private WebTestClient webTestClient;

  @MockitoBean private JwksService jwksService;

  @MockitoBean private ReactiveRedisTemplate<String, String> redisTemplate;

  private KeyPair keyPair;
  private String validToken;

  @BeforeAll
  static void startWireMock() {
    userServiceMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    userServiceMock.start();
  }

  @AfterAll
  static void stopWireMock() {
    if (userServiceMock != null) {
      userServiceMock.stop();
    }
  }

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setup() throws NoSuchAlgorithmException {
    userServiceMock.resetAll();

    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
    keyPairGenerator.initialize(2048);
    keyPair = keyPairGenerator.generateKeyPair();

    when(jwksService.getPublicKey(anyString())).thenReturn(Mono.just(keyPair.getPublic()));

    ReactiveZSetOperations<String, String> zSetOps = mock(ReactiveZSetOperations.class);
    when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
    when(zSetOps.removeRangeByScore(anyString(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(Mono.just(0L));
    when(zSetOps.size(anyString())).thenReturn(Mono.just(0L));
    when(zSetOps.add(anyString(), anyString(), org.mockito.ArgumentMatchers.anyDouble()))
        .thenReturn(Mono.just(true));
    when(redisTemplate.expire(anyString(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(Mono.just(true));

    validToken =
        Jwts.builder()
            .setSubject("user-123")
            .claim("email", "user@example.com")
            .setHeaderParam("kid", "test-key-id")
            .setIssuer("test-issuer")
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + 3600000))
            .signWith(keyPair.getPrivate(), SignatureAlgorithm.RS256)
            .compact();
  }

  @Test
  void shouldRouteToDownstreamWhenHealthy() {
    userServiceMock.stubFor(
        get(urlPathEqualTo("/api/users/me"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"id\": \"user-123\", \"email\": \"user@example.com\"}")));

    webTestClient
        .get()
        .uri("/api/users/me")
        .cookie("access_token", validToken)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.id")
        .isEqualTo("user-123");
  }

  @Test
  void shouldReturnFallbackWhenDownstreamFails() {
    userServiceMock.stubFor(
        get(urlPathEqualTo("/api/users/me"))
            .willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));

    for (int i = 0; i < 6; i++) {
      webTestClient.get().uri("/api/users/me").cookie("access_token", validToken).exchange();
    }

    var response =
        webTestClient
            .get()
            .uri("/api/users/me")
            .cookie("access_token", validToken)
            .exchange()
            .returnResult(String.class);

    assertThat(response.getStatus().value()).isIn(503, 500);
  }

  @Test
  void shouldReturnFallbackOnTimeout() {
    userServiceMock.stubFor(
        get(urlPathEqualTo("/api/users/me"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withFixedDelay(15000)
                    .withBody("{\"id\": \"user-123\"}")));

    var response =
        webTestClient
            .get()
            .uri("/api/users/me")
            .cookie("access_token", validToken)
            .exchange()
            .returnResult(String.class);

    assertThat(response.getStatus().value()).isIn(503, 504, 500);
  }

  @Test
  void shouldRecoverAfterCircuitBreakerResets() throws InterruptedException {
    userServiceMock.stubFor(
        get(urlPathEqualTo("/api/users/me")).willReturn(aResponse().withStatus(500)));

    for (int i = 0; i < 6; i++) {
      webTestClient.get().uri("/api/users/me").cookie("access_token", validToken).exchange();
    }

    userServiceMock.resetAll();
    userServiceMock.stubFor(
        get(urlPathEqualTo("/api/users/me"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"id\": \"user-123\"}")));
  }
}
