package io.github.joaosimsic.filters;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class TraceIdFilter implements GlobalFilter, Ordered {

  private static final String TRACE_ID_HEADER = "X-Trace-Id";

  @Override
  public int getOrder() {
    return -200;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String traceId = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);

    if (traceId == null || traceId.isBlank()) {
      traceId = UUID.randomUUID().toString().substring(0, 8);
    }

    ServerHttpRequest mutatedRequest =
        exchange.getRequest().mutate().header(TRACE_ID_HEADER, traceId).build();

    return chain.filter(exchange.mutate().request(mutatedRequest).build());
  }
}
