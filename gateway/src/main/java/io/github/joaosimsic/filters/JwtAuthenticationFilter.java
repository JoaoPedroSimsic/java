package io.github.joaosimsic.filters;

import io.github.joaosimsic.config.GatewayProperties;
import io.github.joaosimsic.services.JwksService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
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
      List.of("/swagger-ui", "/v3/api-docs", "/actuator/health", "/api/auth");

  @Override
  public int getOrder() {
    return -100;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String path = exchange.getRequest().getPath().value();

    if (isPublicPath(path)) {
      return chain.filter(exchange);
    }

    return Mono.justOrEmpty(extractToken(exchange))
        .flatMap(this::validateToken)
        .flatMap(claims -> chain.filter(mutateExchange(exchange, claims)))
        .switchIfEmpty(
            Mono.defer(
                () -> onError(exchange, "Missing access_token cookie", HttpStatus.UNAUTHORIZED)))
        .onErrorResume(e -> onError(exchange, e.getMessage(), HttpStatus.UNAUTHORIZED));
  }

  private ServerWebExchange mutateExchange(ServerWebExchange exchange, Claims claims) {
    String userId = claims.getSubject();

    String userEmail = Optional.ofNullable(claims.get("email")).map(Object::toString).orElse("");

    exchange.getAttributes().put("userId", userId);
    exchange.getAttributes().put("userEmail", userEmail);
    exchange.getAttributes().put("authenticated", true);

    return exchange
        .mutate()
        .request(
            exchange
                .getRequest()
                .mutate()
                .header("X-User-Email", userEmail)
                .header("X-User-Id", userId != null ? userId : "")
                .header("X-Gateway-Secret", props.secret())
                .build())
        .build();
  }

  private Mono<Claims> validateToken(String token) {
    return Mono.fromCallable(
            () -> {
              Object kid =
                  Jwts.parserBuilder()
                      .build()
                      .parse(token.substring(0, token.lastIndexOf('.') + 1))
                      .getHeader()
                      .get("kid");

              if (kid == null) throw new IllegalArgumentException("Missing Key ID (kid)");
              return kid.toString();
            })
        .flatMap(jwksService::getPublicKey)
        .map(
            publicKey ->
                Jwts.parserBuilder()
                    .setSigningKey(publicKey)
                    .requireIssuer(props.jwt().expectedIssuer())
                    .build()
                    .parseClaimsJws(token)
                    .getBody());
  }

  private Optional<String> extractToken(ServerWebExchange exchange) {
    return Optional.ofNullable(exchange.getRequest().getCookies().getFirst("access_token"))
        .map(HttpCookie::getValue);
  }

  private boolean isPublicPath(String path) {
    return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
  }

  private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus status) {
    if (!exchange.getResponse().isCommitted()) {
      log.warn("Auth failed for {}: {}", exchange.getRequest().getPath(), err);

      exchange.getResponse().setStatusCode(status);

      return exchange.getResponse().setComplete();
    }

    return Mono.empty();
  }
}
