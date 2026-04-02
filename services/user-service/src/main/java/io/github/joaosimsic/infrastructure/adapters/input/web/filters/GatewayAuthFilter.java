package io.github.joaosimsic.infrastructure.adapters.input.web.filters;

import io.github.joaosimsic.infrastructure.config.properties.JwtProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@RequiredArgsConstructor
public class GatewayAuthFilter extends OncePerRequestFilter {

  private static final String GATEWAY_SECRET_HEADER = "X-Gateway-Secret";
  private static final String USER_ID_HEADER = "X-User-Id";
  private static final String USER_EMAIL_HEADER = "X-User-Email";
  private static final List<String> PUBLIC_PATHS =
      List.of("/actuator/health", "/swagger-ui", "/v3/api-docs");

  private final JwtProperties jwtProperties;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String secret = request.getHeader(GATEWAY_SECRET_HEADER);
    String expectedSecret = jwtProperties.secret();
    
    log.info("GatewayAuthFilter: path={}, receivedSecret={}, expectedSecret={}", 
        request.getRequestURI(),
        secret != null ? secret.substring(0, Math.min(10, secret.length())) + "..." : "null",
        expectedSecret != null ? expectedSecret.substring(0, Math.min(10, expectedSecret.length())) + "..." : "null");

    if (secret == null || !expectedSecret.equals(secret)) {
      log.warn("GatewayAuthFilter: Secret mismatch - received={}, expected={}", secret, expectedSecret);
      throw new BadCredentialsException("Invalid or missing gateway secret");
    }

    String userId = request.getHeader(USER_ID_HEADER);
    String userEmail = request.getHeader(USER_EMAIL_HEADER);

    if (userId != null && !userId.isEmpty()) {
      GatewayPrincipal principal = new GatewayPrincipal(userId, userEmail);
      var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
      SecurityContextHolder.getContext().setAuthentication(auth);
      log.info("GatewayAuthFilter: Authenticated user={}", userId);
    }

    filterChain.doFilter(request, response);
  }
}
