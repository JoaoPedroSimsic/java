package io.github.joaosimsic;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.github.joaosimsic.services.JwksService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class GatewayApplicationTest {

  @Autowired protected WebTestClient webTestClient;

  @MockitoBean protected JwksService jwksService;

  @MockitoBean protected ReactiveRedisTemplate<String, String> redisTemplate;

  protected String validToken;
  protected final String TEST_EMAIL = "user@example.com";

  @BeforeEach
  void setup() throws NoSuchAlgorithmException {
    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");

    keyPairGenerator.initialize(2048);

    KeyPair keyPair = keyPairGenerator.generateKeyPair();

    when(jwksService.getPublicKey(anyString())).thenReturn(Mono.just(keyPair.getPublic()));

    validToken =
        Jwts.builder()
            .setSubject("user-123")
            .claim("email", TEST_EMAIL)
            .setHeaderParam("kid", "test-key")
            .setIssuer("test-issuer")
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + 3600000))
            .signWith(keyPair.getPrivate(), SignatureAlgorithm.RS256)
            .compact();
  }
}
