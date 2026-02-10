package io.github.joaosimsic.infrastructure.adapters.input.web.filters;

import io.github.joaosimsic.infrastructure.config.properties.JwtProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
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

  private final JwtProperties jwtProperties;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String secret = request.getHeader(GATEWAY_SECRET_HEADER);

    if (secret == null || !jwtProperties.getSecret().equals(secret)) {
      throw new BadCredentialsException("Invalid or missing gateway secret");
    }

    String userId = request.getHeader(USER_ID_HEADER);
    String userEmail = request.getHeader(USER_EMAIL_HEADER);

    if (userId != null && !userId.isEmpty()) {
      GatewayPrincipal principal = new GatewayPrincipal(userId, userEmail);
      var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
      SecurityContextHolder.getContext().setAuthentication(auth);
      log.debug("Authenticated request from gateway for user: {}", userId);
    }

    filterChain.doFilter(request, response);
  }
}
