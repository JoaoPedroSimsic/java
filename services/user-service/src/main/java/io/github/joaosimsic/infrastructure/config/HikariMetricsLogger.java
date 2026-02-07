package io.github.joaosimsic.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;

import lombok.extern.slf4j.Slf4j;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "hikari.metrics.enabled", havingValue = "true")
@Slf4j
public class HikariMetricsLogger {
  private final HikariDataSource dataSource;

  public HikariMetricsLogger(HikariDataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Scheduled(fixedRate = 60000)
  public void logHikariMetrics() {
    HikariPoolMXBean poolMXBean = dataSource.getHikariPoolMXBean();

    log.info(
        "HikariCP Metrics: Total Connections: {}, Active Connections: {}, Idle Connections: {}, Threads Awaiting Connection: {}",
        poolMXBean.getTotalConnections(),
        poolMXBean.getActiveConnections(),
        poolMXBean.getIdleConnections(),
        poolMXBean.getThreadsAwaitingConnection());
  }
}
