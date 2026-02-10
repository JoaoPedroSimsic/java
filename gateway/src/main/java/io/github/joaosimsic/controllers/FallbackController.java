package io.github.joaosimsic.controllers;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

  @GetMapping("/user-service")
  public ResponseEntity<Map<String, Object>> userServiceFallback() {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(
            Map.of(
                "error", "Service Unavailable",
                "message", "User service is currently unavailable. Please try again later.",
                "service", "user-service"));
  }
}
