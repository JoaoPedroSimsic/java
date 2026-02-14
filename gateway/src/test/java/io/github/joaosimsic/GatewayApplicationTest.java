package io.github.joaosimsic;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureWebTestClient(timeout = "30000")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class GatewayApplicationTest {

  protected static WireMockServer authServiceMock;
  protected static WireMockServer userServiceMock;

  @Autowired protected WebTestClient webTestClient;

  @MockitoBean protected JwksService jwksService;

  @MockitoBean protected ReactiveRedisTemplate<String, String> redisTemplate;

  protected String validToken;
  protected final String TEST_EMAIL = "user@example.com";

  protected KeyPair keyPair;

  @BeforeAll
  static void startWireMock() {
    authServiceMock = new WireMockServer(WireMockConfiguration.wireMockConfig().port(8081));
    userServiceMock = new WireMockServer(WireMockConfiguration.wireMockConfig().port(8082));
    authServiceMock.start();
    userServiceMock.start();
  }

  @AfterAll
  static void stopWireMock() {
    if (authServiceMock != null) {
      authServiceMock.stop();
    }
    if (userServiceMock != null) {
      userServiceMock.stop();
    }
  }



  @BeforeEach
  void setup() throws NoSuchAlgorithmException {
    reset(jwksService, redisTemplate);
    authServiceMock.resetAll();
    userServiceMock.resetAll();

    // Stub default responses for downstream services
    authServiceMock.stubFor(
        any(urlPathMatching("/api/auth/.*"))
            .willReturn(aResponse().withStatus(200).withBody("{}")));

    userServiceMock.stubFor(
        any(urlPathMatching("/api/users/.*"))
            .willReturn(aResponse().withStatus(200).withBody("{}")));

    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
    keyPairGenerator.initialize(2048);
    keyPair = keyPairGenerator.generateKeyPair();

    when(jwksService.getPublicKey(anyString())).thenReturn(Mono.just(keyPair.getPublic()));

    validToken =
        Jwts.builder()
            .setSubject("user-123")
            .claim("email", TEST_EMAIL)
            .setHeaderParam("kid", "test-key-id")
            .setIssuer("test-issuer")
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + 3600000))
            .signWith(keyPair.getPrivate(), SignatureAlgorithm.RS256)
            .compact();
  }
}
