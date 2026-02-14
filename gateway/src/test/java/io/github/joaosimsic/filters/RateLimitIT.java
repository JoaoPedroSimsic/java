package io.github.joaosimsic.filters;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;

@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class RateLimitIT {

  @Container static RedisContainer redis = new RedisContainer(RedisContainer.DEFAULT_IMAGE_NAME);

  private static WireMockServer authServiceMock;
  private static WireMockServer userServiceMock;

  @BeforeAll
  static void startWireMock() {
    authServiceMock = new WireMockServer(WireMockConfiguration.wireMockConfig().port(8081));
    userServiceMock = new WireMockServer(WireMockConfiguration.wireMockConfig().port(8082));
    authServiceMock.start();
    userServiceMock.start();

    authServiceMock.stubFor(
        any(urlPathMatching("/api/auth/.*"))
            .willReturn(aResponse().withStatus(200).withBody("{}")));
    userServiceMock.stubFor(
        any(urlPathMatching("/api/users/.*"))
            .willReturn(aResponse().withStatus(200).withBody("{}")));
  }

  @AfterAll
  static void stopWireMock() {
    if (authServiceMock != null) authServiceMock.stop();
    if (userServiceMock != null) userServiceMock.stop();
  }

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", redis::getFirstMappedPort);
  }

  @Autowired private WebTestClient webTestClient;

  @MockitoBean private JwksService jwksService;

  private KeyPair keyPair;
  private String validToken;
  private static final String TEST_EMAIL = "ratelimit-test@example.com";

  @BeforeEach
  void setup() throws NoSuchAlgorithmException {
    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
    keyPairGenerator.initialize(2048);
    keyPair = keyPairGenerator.generateKeyPair();

    when(jwksService.getPublicKey(anyString())).thenReturn(Mono.just(keyPair.getPublic()));

    validToken =
        Jwts.builder()
            .setSubject("user-rate-limit-test")
            .claim("email", TEST_EMAIL)
            .setHeaderParam("kid", "test-key-id")
            .setIssuer("test-issuer")
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + 3600000))
            .signWith(keyPair.getPrivate(), SignatureAlgorithm.RS256)
            .compact();
  }

  @Test
  void shouldApplyRateLimitToUnauthenticatedRequests() {
    for (int i = 0; i < 10; i++) {
      webTestClient
          .get()
          .uri("/api/auth/login")
          .exchange()
          .expectHeader()
          .exists("X-RateLimit-Remaining");
    }

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
  void shouldApplyHigherRateLimitToAuthenticatedRequests() {
    for (int i = 0; i < 15; i++) {
      webTestClient
          .get()
          .uri("/api/users/me")
          .cookie("access_token", validToken)
          .exchange()
          .expectHeader()
          .exists("X-RateLimit-Remaining");
    }

    var response =
        webTestClient
            .get()
            .uri("/api/users/me")
            .cookie("access_token", validToken)
            .exchange()
            .expectHeader()
            .exists("X-RateLimit-Remaining")
            .returnResult(String.class);

    String remaining = response.getResponseHeaders().getFirst("X-RateLimit-Remaining");
    assertThat(remaining).isNotNull();
    assertThat(Integer.parseInt(remaining)).isGreaterThan(100);
  }

  @Test
  void shouldReturnCorrectRateLimitHeaders() {
    var response = webTestClient.get().uri("/api/auth/login").exchange().returnResult(String.class);

    assertThat(response.getResponseHeaders().getFirst("X-RateLimit-Limit")).isNotNull();
    assertThat(response.getResponseHeaders().getFirst("X-RateLimit-Remaining")).isNotNull();
    assertThat(response.getResponseHeaders().getFirst("X-RateLimit-Reset")).isNotNull();
  }

  @Test
  void shouldDifferentiateByUserEmailForAuthenticatedRequests() throws NoSuchAlgorithmException {
    String user1Email = "differentiation-user1-" + System.currentTimeMillis() + "@example.com";
    String user2Email = "differentiation-user2-" + System.currentTimeMillis() + "@example.com";

    String user1Token =
        Jwts.builder()
            .setSubject("user1-differentiation")
            .claim("email", user1Email)
            .setHeaderParam("kid", "test-key-id")
            .setIssuer("test-issuer")
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + 3600000))
            .signWith(keyPair.getPrivate(), SignatureAlgorithm.RS256)
            .compact();

    String user2Token =
        Jwts.builder()
            .setSubject("user2-differentiation")
            .claim("email", user2Email)
            .setHeaderParam("kid", "test-key-id")
            .setIssuer("test-issuer")
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + 3600000))
            .signWith(keyPair.getPrivate(), SignatureAlgorithm.RS256)
            .compact();

    var response1 =
        webTestClient
            .get()
            .uri("/api/users/me")
            .cookie("access_token", user1Token)
            .exchange()
            .returnResult(String.class);

    String remaining1 = response1.getResponseHeaders().getFirst("X-RateLimit-Remaining");

    var response2 =
        webTestClient
            .get()
            .uri("/api/users/me")
            .cookie("access_token", user2Token)
            .exchange()
            .returnResult(String.class);

    String remaining2 = response2.getResponseHeaders().getFirst("X-RateLimit-Remaining");

    assertThat(remaining1).isNotNull();
    assertThat(remaining2).isNotNull();
    assertThat(Integer.parseInt(remaining1)).isGreaterThan(140);
    assertThat(Integer.parseInt(remaining2)).isGreaterThan(140);
  }
}
