package io.github.joaosimsic.infrastructure.adapters.output.idp;

import io.github.joaosimsic.core.domain.AuthTokens;
import io.github.joaosimsic.core.domain.AuthUser;
import io.github.joaosimsic.core.exceptions.business.AuthenticationException;
import io.github.joaosimsic.core.exceptions.business.UserAlreadyExistsException;
import io.github.joaosimsic.core.ports.output.AuthPort;
import io.github.joaosimsic.infrastructure.config.properties.KeycloakProperties;
import jakarta.ws.rs.core.Response;
import java.util.Collections;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class KeycloakAdapter implements AuthPort {

  private final Keycloak keycloakAdminClient;
  private final KeycloakProperties keycloakProperties;
  private final WebClient webClient = WebClient.builder().build();

  @Override
  public AuthUser createUser(String name, String email, String password) {
    RealmResource realmResource = keycloakAdminClient.realm(keycloakProperties.getRealm());

    UsersResource usersResource = realmResource.users();

    UserRepresentation user = new UserRepresentation();

    user.setUsername(email);
    user.setEmail(email);
    user.setEnabled(true);
    user.setEmailVerified(true);
    user.setRequiredActions(Collections.emptyList());
    user.singleAttribute("name", name);

    CredentialRepresentation credential = new CredentialRepresentation();

    credential.setType(CredentialRepresentation.PASSWORD);
    credential.setValue(password);
    credential.setTemporary(false);
    user.setCredentials(Collections.singletonList(credential));

    try (Response response = usersResource.create(user)) {
      if (response.getStatus() == 409) {
        throw new UserAlreadyExistsException("User with email " + email + " already exists");
      }

      if (response.getStatus() == 201) {
        String userId = extractUserIdFromLocation(response);

        log.info("User created successfully in Keycloak with ID: {}", userId);

        return AuthUser.builder().id(userId).email(email).name(name).emailVerified(true).build();
      }

      String errorBody = response.readEntity(String.class);
      log.error(
          "Failed to create user in Keycloak. Status: {}, Body: {}",
          response.getStatus(),
          errorBody);

      throw new AuthenticationException("Failed to create user: " + errorBody);
    }
  }

  @Override
  public AuthTokens login(String email, String password) {
    String tokenUrl =
        keycloakProperties.getServerUrl()
            + "/realms/"
            + keycloakProperties.getRealm()
            + "/protocol/openid-connect/token";

    MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();

    formData.add("grant_type", "password");
    formData.add("client_id", keycloakProperties.getClientId());
    formData.add("username", email);
    formData.add("password", password);
    formData.add("scope", "openid email profile");

    try {
      Map<String, Object> response =
          webClient
              .post()
              .uri(tokenUrl)
              .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
              .body(BodyInserters.fromFormData(formData))
              .retrieve()
              .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
              .block();

      return mapToAuthTokens(response);
    } catch (WebClientResponseException e) {
      log.error("Login failed for user: {}. Status: {}", email, e.getStatusCode());

      throw new AuthenticationException("Invalid credentials");
    }
  }

  @Override
  public AuthTokens refreshToken(String refreshToken) {
    String tokenUrl =
        keycloakProperties.getServerUrl()
            + "/realms/"
            + keycloakProperties.getRealm()
            + "/protocol/openid-connect/token";

    MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
    formData.add("grant_type", "refresh_token");
    formData.add("client_id", keycloakProperties.getClientId());
    formData.add("refresh_token", refreshToken);

    try {
      Map<String, Object> response =
          webClient
              .post()
              .uri(tokenUrl)
              .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
              .body(BodyInserters.fromFormData(formData))
              .retrieve()
              .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
              .block();

      return mapToAuthTokens(response);
    } catch (WebClientResponseException e) {
      log.error("Token refresh failed. Status: {}", e.getStatusCode());

      throw new AuthenticationException("Failed to refresh token");
    }
  }

  @Override
  public void logout(String accessToken) {
    String revokeUrl =
        keycloakProperties.getServerUrl()
            + "/realms/"
            + keycloakProperties.getRealm()
            + "/protocol/openid-connect/revoke";

    MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
    formData.add("client_id", keycloakProperties.getClientId());
    formData.add("token", accessToken);
    formData.add("token_type_hint", "access_token");

    try {
      webClient
          .post()
          .uri(revokeUrl)
          .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
          .body(BodyInserters.fromFormData(formData))
          .retrieve()
          .toBodilessEntity()
          .block();

      log.info("User logged out successfully");
    } catch (WebClientResponseException e) {
      log.warn("Logout request failed. Status: {}", e.getStatusCode());
    }
  }

  @Override
  public String getGitHubAuthUrl(String redirectUri, String state) {
    return UriComponentsBuilder.fromUriString(keycloakProperties.getServerUrl())
        .path("/realms/" + keycloakProperties.getRealm() + "/protocol/openid-connect/auth")
        .queryParam("client_id", keycloakProperties.getClientId())
        .queryParam("redirect_uri", redirectUri)
        .queryParam("response_type", "code")
        .queryParam("scope", "openid email profile")
        .queryParam("kc_idp_hint", "github")
        .queryParam("state", state)
        .build()
        .toUriString();
  }

  @Override
  public AuthTokens exchangeCodeForTokens(String code, String redirectUri) {
    String tokenUrl =
        keycloakProperties.getServerUrl()
            + "/realms/"
            + keycloakProperties.getRealm()
            + "/protocol/openid-connect/token";

    MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
    formData.add("grant_type", "authorization_code");
    formData.add("client_id", keycloakProperties.getClientId());
    formData.add("code", code);
    formData.add("redirect_uri", redirectUri);

    try {
      Map<String, Object> response =
          webClient
              .post()
              .uri(tokenUrl)
              .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
              .body(BodyInserters.fromFormData(formData))
              .retrieve()
              .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
              .block();

      return mapToAuthTokens(response);
    } catch (WebClientResponseException e) {
      log.error("Code exchange failed. Status: {}", e.getStatusCode());

      throw new AuthenticationException("Failed to exchange authorization code");
    }
  }

  @Override
  public AuthUser getUserInfo(String accessToken) {
    String userInfoUrl =
        keycloakProperties.getServerUrl()
            + "/realms/"
            + keycloakProperties.getRealm()
            + "/protocol/openid-connect/userinfo";

    try {
      Map<String, Object> response =
          webClient
              .get()
              .uri(userInfoUrl)
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
              .retrieve()
              .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
              .block();

      return AuthUser.builder()
          .id((String) response.get("sub"))
          .email((String) response.get("email"))
          .name((String) response.get("name"))
          .emailVerified(Boolean.TRUE.equals(response.get("email_verified")))
          .build();
    } catch (WebClientResponseException e) {
      log.error("Failed to get user info. Status: {}", e.getStatusCode());

      throw new AuthenticationException("Failed to get user info");
    }
  }

  private String extractUserIdFromLocation(Response response) {
    String location = response.getHeaderString("Location");

    if (location == null || !location.contains("/")) {
      throw new AuthenticationException("Could not extract user ID from response");
    }

    return location.substring(location.lastIndexOf("/") + 1);
  }

  private AuthTokens mapToAuthTokens(Map<String, Object> response) {
    return AuthTokens.builder()
        .accessToken((String) response.get("access_token"))
        .refreshToken((String) response.get("refresh_token"))
        .idToken((String) response.get("id_token"))
        .expiresIn((Integer) response.get("expires_in"))
        .refreshExpiresIn((Integer) response.get("refresh_expires_in"))
        .build();
  }

  @Override
  public void updateEmail(String userId, String newEmail) {
    log.info("Updating email for user {} in Keycloak", userId);

    RealmResource realmResource = keycloakAdminClient.realm(keycloakProperties.getRealm());
    UsersResource usersResource = realmResource.users();

    try {
      UserRepresentation user = usersResource.get(userId).toRepresentation();
      user.setEmail(newEmail);
      user.setUsername(newEmail);

      usersResource.get(userId).update(user);

      log.info("Email updated successfully for user {} in Keycloak", userId);
    } catch (Exception e) {
      log.error("Failed to update email for user {} in Keycloak: {}", userId, e.getMessage());
      throw new AuthenticationException("Failed to update email: " + e.getMessage());
    }
  }

  @Override
  public void updatePassword(String userId, String currentPassword, String newPassword) {
    log.info("Updating password for user {} in Keycloak", userId);

    RealmResource realmResource = keycloakAdminClient.realm(keycloakProperties.getRealm());
    UsersResource usersResource = realmResource.users();

    try {
      UserRepresentation user = usersResource.get(userId).toRepresentation();

      verifyCurrentPassword(user.getEmail(), currentPassword);

      CredentialRepresentation credential = new CredentialRepresentation();
      credential.setType(CredentialRepresentation.PASSWORD);
      credential.setValue(newPassword);
      credential.setTemporary(false);

      usersResource.get(userId).resetPassword(credential);

      log.info("Password updated successfully for user {} in Keycloak", userId);
    } catch (AuthenticationException e) {
      throw e;
    } catch (Exception e) {
      log.error("Failed to update password for user {} in Keycloak: {}", userId, e.getMessage());
      throw new AuthenticationException("Failed to update password: " + e.getMessage());
    }
  }

  private void verifyCurrentPassword(String email, String currentPassword) {
    try {
      login(email, currentPassword);
    } catch (AuthenticationException e) {
      throw new AuthenticationException("Current password is incorrect");
    }
  }
}
