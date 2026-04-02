package io.github.joaosimsic.infrastructure.adapters.input.web.filters;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class TraceIdFilter implements Filter {

  private static final String TRACE_ID = "traceId";
  private static final String TRACE_ID_HEADER = "X-Trace-Id";

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain)
      throws IOException, ServletException {
    try {
      String traceId = null;

      if (request instanceof HttpServletRequest httpRequest) {
        traceId = httpRequest.getHeader(TRACE_ID_HEADER);
      }

      if (traceId == null || traceId.isBlank()) {
        traceId = UUID.randomUUID().toString().substring(0, 8);
      }

      MDC.put(TRACE_ID, traceId);
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(TRACE_ID);
    }
  }
}
