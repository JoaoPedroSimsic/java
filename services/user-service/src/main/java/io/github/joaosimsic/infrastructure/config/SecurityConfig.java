package io.github.joaosimsic.infrastructure.config;

import io.github.joaosimsic.infrastructure.adapters.input.web.filters.GatewayAuthFilter;
import io.github.joaosimsic.infrastructure.adapters.input.web.filters.GatewayAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final GatewayAuthenticationEntryPoint authenticationEntryPoint;
  private final GatewayAuthFilter gatewayAuthFilter;

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/actuator/health")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/users/sync")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(gatewayAuthFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
