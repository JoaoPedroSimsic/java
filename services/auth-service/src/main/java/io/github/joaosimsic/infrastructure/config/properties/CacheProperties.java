package io.github.joaosimsic.infrastructure.config.properties;

import com.sun.istack.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "spring.cache")
public record CacheProperties(@NotNull Duration ttl, @NotNull boolean cacheNullValues) {}
