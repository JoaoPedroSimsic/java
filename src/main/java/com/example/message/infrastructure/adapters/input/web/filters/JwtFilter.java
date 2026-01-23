package com.example.message.infrastructure.adapters.input.web.filters;

import com.example.message.core.domain.User;
import com.example.message.core.ports.output.TokenRepositoryPort;
import com.example.message.core.ports.output.UserRepositoryPort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtFilter extends OncePerRequestFilter {
  private final TokenRepositoryPort tokenRepositoryPort;
  private final UserRepositoryPort userRepositoryPort;

  public JwtFilter(TokenRepositoryPort tokenRepositoryPort, UserRepositoryPort userRepositoryPort) {
    this.tokenRepositoryPort = tokenRepositoryPort;
    this.userRepositoryPort = userRepositoryPort;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String authHeader = request.getHeader("Authorization");

    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      String token = authHeader.substring(7);
      String email = tokenRepositoryPort.validateToken(token);

      if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
        User user = userRepositoryPort.findByEmail(email);
        var authToken =
            new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authToken);
      }
    }

    filterChain.doFilter(request, response);
  }
}
