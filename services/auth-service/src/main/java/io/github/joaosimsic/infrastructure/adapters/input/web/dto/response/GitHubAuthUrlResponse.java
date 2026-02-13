package io.github.joaosimsic.infrastructure.adapters.input.web.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GitHubAuthUrlResponse {
  private String authUrl;
  private String state;
}
