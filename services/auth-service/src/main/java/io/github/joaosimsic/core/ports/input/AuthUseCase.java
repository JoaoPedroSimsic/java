package io.github.joaosimsic.core.ports.input;

import io.github.joaosimsic.core.domain.AuthTokens;
import io.github.joaosimsic.core.domain.AuthUser;

public interface AuthUseCase {
  AuthTokens register(String name, String email, String password);
  
  AuthTokens login(String email, String password);
  
  AuthTokens refresh(String refreshToken);
  
  void logout(String refreshToken);
  
  String getGitHubAuthUrl(String redirectUri, String state);
  
  AuthTokens handleGitHubCallback(String code, String redirectUri);
  
  AuthUser getCurrentUser(String accessToken);
}
