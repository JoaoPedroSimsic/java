package io.github.joaosimsic.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class JwksService {

  private final GatewayProperties gatewayProperties;
  private final WebClient.Builder webClientBuilder;
  private final ObjectMapper objectMapper = new ObjectMapper();

  private final Map<String, PublicKey> keyCache = new ConcurrentHashMap<>();
  private volatile Instant lastFetchTime = Instant.EPOCH;

  @PostConstruct
  public void init() {
    refreshKeys()
        .subscribe(
            success -> log.info("Successfully fetched JWKS keys on startup"),
            error ->
                log.warn(
                    "Failed to fetch JWKS keys on startup, will retry on first request: {}",
                    error.getMessage()));
  }

  public Mono<PublicKey> getPublicKey(String keyId) {
    if (isCacheValid() && keyCache.containsKey(keyId)) {
      return Mono.just(keyCache.get(keyId));
    }

    return refreshKeys()
        .then(
            Mono.defer(
                () -> {
                  PublicKey key = keyCache.get(keyId);
                  if (key == null) {
                    return Mono.error(new IllegalArgumentException("Unknown key ID: " + keyId));
                  }
                  return Mono.just(key);
                }));
  }

  private boolean isCacheValid() {
    return !keyCache.isEmpty()
        && Duration.between(lastFetchTime, Instant.now()).getSeconds()
            < gatewayProperties.getJwt().getCacheTtlSeconds();
  }

  private Mono<Void> refreshKeys() {
    return webClientBuilder
        .build()
        .get()
        .uri(gatewayProperties.getJwt().getJwksUrl())
        .retrieve()
        .bodyToMono(String.class)
        .flatMap(this::parseJwks)
        .doOnSuccess(
            v -> {
              lastFetchTime = Instant.now();
              log.debug(
                  "JWKS keys refreshed successfully, cache contains {} keys", keyCache.size());
            })
        .doOnError(e -> log.error("Failed to refresh JWKS keys: {}", e.getMessage()));
  }

  private Mono<Void> parseJwks(String jwksJson) {
    return Mono.fromCallable(
            () -> {
              JsonNode root = objectMapper.readTree(jwksJson);
              JsonNode keys = root.get("keys");

              if (keys == null || !keys.isArray()) {
                throw new IllegalStateException("Invalid JWKS response: missing 'keys' array");
              }

              Map<String, PublicKey> newKeys = new ConcurrentHashMap<>();

              for (JsonNode keyNode : keys) {
                String kty = keyNode.get("kty").asText();
                if (!"RSA".equals(kty)) {
                  continue;
                }

                String kid = keyNode.get("kid").asText();
                String n = keyNode.get("n").asText();
                String e = keyNode.get("e").asText();

                PublicKey publicKey = buildRsaPublicKey(n, e);
                newKeys.put(kid, publicKey);
              }

              keyCache.clear();
              keyCache.putAll(newKeys);

              return null;
            })
        .then();
  }

  private PublicKey buildRsaPublicKey(String modulusBase64, String exponentBase64)
      throws Exception {
    byte[] modulusBytes = Base64.getUrlDecoder().decode(modulusBase64);
    byte[] exponentBytes = Base64.getUrlDecoder().decode(exponentBase64);

    BigInteger modulus = new BigInteger(1, modulusBytes);
    BigInteger exponent = new BigInteger(1, exponentBytes);

    RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
    KeyFactory factory = KeyFactory.getInstance("RSA");

    return factory.generatePublic(spec);
  }
}
