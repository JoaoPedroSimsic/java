package io.github.joaosimsic.infrastructure.adapters.input.web.responses;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.Map;

public record ErrorResponse(
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime timestamp,
    int status,
    String error,
    String message,
    String path,
    Map<String, String> details) {
  public ErrorResponse {
    if (timestamp == null) {
      timestamp = LocalDateTime.now();
    }
  }

  public static ErrorResponse of(int status, String error, String message, String path) {
    return new ErrorResponse(LocalDateTime.now(), status, error, message, path, null);
  }
}
