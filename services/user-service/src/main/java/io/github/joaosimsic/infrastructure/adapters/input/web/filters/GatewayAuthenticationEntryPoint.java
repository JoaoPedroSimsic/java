package io.github.joaosimsic.infrastructure.adapters.input.web.filters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.joaosimsic.infrastructure.adapters.input.web.responses.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class GatewayAuthenticationEntryPoint implements AuthenticationEntryPoint {

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {

    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);

    var error =
        ErrorResponse.of(
            HttpServletResponse.SC_FORBIDDEN,
            "FORBIDDEN",
            authException.getMessage(),
            request.getRequestURI());

    new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .writeValue(response.getOutputStream(), error);
  }
}
