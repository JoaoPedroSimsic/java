package io.github.joaosimsic.services;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.joaosimsic.config.GatewayProperties;
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
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Service
@RequiredArgsConstructor
public class JwksService {

  private final GatewayProperties gatewayProperties;
  private final WebClient.Builder webClientBuilder;

  private final Map<String, PublicKey> keyCache = new ConcurrentHashMap<>();
  private volatile Instant lastFetchTime = Instant.EPOCH;

  @PostConstruct
  public void init() {
    refreshKeys()
        .doOnSuccess(v -> log.info("Successfully fetched JWKS keys on startup"))
        .doOnError(
            e -> log.warn("Initial JWKS fetch failed, will retry on demand: {}", e.getMessage()))
        .subscribe();
  }

  public Mono<PublicKey> getPublicKey(String keyId) {
    if (isCacheValid() && keyCache.containsKey(keyId)) {
      return Mono.just(keyCache.get(keyId));
    }

    return refreshKeys()
        .then(Mono.defer(() -> Mono.justOrEmpty(keyCache.get(keyId))))
        .switchIfEmpty(Mono.error(() -> new IllegalArgumentException("Unknown key ID: " + keyId)));
  }

  private boolean isCacheValid() {
    return !keyCache.isEmpty()
        && Duration.between(lastFetchTime, Instant.now()).getSeconds()
            < gatewayProperties.jwt().cacheTtlSeconds();
  }

  private Mono<Void> refreshKeys() {
    return webClientBuilder
        .build()
        .get()
        .uri(gatewayProperties.jwt().jwksUrl())
        .retrieve()
        .bodyToMono(JsonNode.class)
        .publishOn(Schedulers.boundedElastic())
        .map(this::extractKeys)
        .doOnNext(
            newKeys -> {
              keyCache.clear();
              keyCache.putAll(newKeys);
              lastFetchTime = Instant.now();
              log.debug("JWKS refreshed. Cache size: {}", keyCache.size());
            })
        .then()
        .doOnError(e -> log.error("JWKS refresh failed: {}", e.getMessage()));
  }

  private Map<String, PublicKey> extractKeys(JsonNode root) {
    JsonNode keys = root.path("keys");

    if (!keys.isArray()) {
      throw new IllegalStateException("Invalid JWKS: 'keys' array missing");
    }

    return StreamSupport.stream(keys.spliterator(), false)
        .filter(node -> "RSA".equals(node.path("kty").asText()))
        .collect(
            Collectors.toMap(
                node -> node.path("kid").asText(),
                node -> buildRsaKey(node.path("n").asText(), node.path("e").asText())));
  }

  private PublicKey buildRsaKey(String n, String e) {
    try {
      var decoder = Base64.getUrlDecoder();

      var spec =
          new RSAPublicKeySpec(
              new BigInteger(1, decoder.decode(n)), new BigInteger(1, decoder.decode(e)));

      return KeyFactory.getInstance("RSA").generatePublic(spec);
    } catch (Exception ex) {
      throw new RuntimeException("Failed to reconstruct RSA Public Key", ex);
    }
  }
}
