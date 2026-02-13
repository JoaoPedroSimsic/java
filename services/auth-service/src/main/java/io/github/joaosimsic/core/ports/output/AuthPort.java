package io.github.joaosimsic.core.ports.output;

import io.github.joaosimsic.core.domain.AuthTokens;
import io.github.joaosimsic.core.domain.AuthUser;

public interface AuthPort {
  AuthUser createUser(String name, String email, String password);

  AuthTokens login(String email, String password);

  AuthTokens refreshToken(String refreshToken);

  void logout(String accessToken);

  String getGitHubAuthUrl(String redirectUri, String state);

  AuthTokens exchangeCodeForTokens(String code, String redirectUri);

  AuthUser getUserInfo(String accessToken);
}
