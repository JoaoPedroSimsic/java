package io.github.joaosimsic.filters;

import io.github.joaosimsic.config.GatewayProperties;
import io.github.joaosimsic.config.JwksService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import java.security.PublicKey;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

  private final JwksService jwksService;
  private final GatewayProperties props;

  private static final List<String> PUBLIC_PATHS =
      List.of(
          "/swagger-ui",
          "/v3/api-docs",
          "/actuator/health");

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    ServerHttpRequest request = exchange.getRequest();
    String path = request.getPath().value();

    if (isPublicPath(path)) {
      return chain.filter(exchange);
    }

    if ("POST".equals(request.getMethod().name()) && "/api/users".equals(path)) {
      return chain.filter(exchange);
    }

    String token = extractToken(exchange);
    if (token == null) {
      log.debug("No JWT token found in request to {}", path);
      exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
      return exchange.getResponse().setComplete();
    }

    return validateToken(token)
        .flatMap(
            claims -> {
              // Standard OIDC claims: sub = user ID from provider, email = user email
              String userId = claims.getSubject();
              String userEmail = claims.get("email", String.class);

              ServerWebExchange mutatedExchange =
                  exchange
                      .mutate()
                      .request(
                          request
                              .mutate()
                              .header("X-User-Email", userEmail != null ? userEmail : "")
                              .header("X-User-Id", userId != null ? userId : "")
                              .header("X-Gateway-Secret", props.getSecret())
                              .build())
                      .build();

              mutatedExchange.getAttributes().put("userEmail", userEmail);
              mutatedExchange.getAttributes().put("userId", userId);
              mutatedExchange.getAttributes().put("authenticated", true);

              return chain.filter(mutatedExchange);
            })
        .onErrorResume(
            e -> {
              log.warn("JWT validation failed for request to {}: {}", path, e.getMessage());
              exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
              return exchange.getResponse().setComplete();
            });
  }

  private boolean isPublicPath(String path) {
    return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
  }

  private String extractToken(ServerWebExchange exchange) {
    HttpCookie jwtCookie = exchange.getRequest().getCookies().getFirst("jwt");
    if (jwtCookie != null) {
      return jwtCookie.getValue();
    }

    String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      return authHeader.substring(7);
    }

    return null;
  }

  private Mono<Claims> validateToken(String token) {
    return Mono.defer(
        () -> {
          String[] parts = token.split("\\.");
          if (parts.length != 3) {
            return Mono.error(new IllegalArgumentException("Invalid JWT format"));
          }

          String headerJson = new String(java.util.Base64.getUrlDecoder().decode(parts[0]));
          String keyId = extractKeyId(headerJson);

          if (keyId == null) {
            return Mono.error(new IllegalArgumentException("No key ID in token header"));
          }

          return jwksService
              .getPublicKey(keyId)
              .flatMap(publicKey -> validateWithKey(token, publicKey));
        });
  }

  private String extractKeyId(String headerJson) {
    int kidIndex = headerJson.indexOf("\"kid\"");
    if (kidIndex == -1) {
      return null;
    }

    int colonIndex = headerJson.indexOf(":", kidIndex);
    int startQuote = headerJson.indexOf("\"", colonIndex);
    int endQuote = headerJson.indexOf("\"", startQuote + 1);

    if (startQuote == -1 || endQuote == -1) {
      return null;
    }

    return headerJson.substring(startQuote + 1, endQuote);
  }

  private Mono<Claims> validateWithKey(String token, PublicKey publicKey) {
    return Mono.fromCallable(
        () -> {
          JwtParser parser =
              Jwts.parserBuilder()
                  .setSigningKey(publicKey)
                  .requireIssuer(props.getJwt().getExpectedIssuer())
                  .build();

          Jws<Claims> jws = parser.parseClaimsJws(token);
          return jws.getBody();
        });
  }

  @Override
  public int getOrder() {
    return -100;
  }
}
