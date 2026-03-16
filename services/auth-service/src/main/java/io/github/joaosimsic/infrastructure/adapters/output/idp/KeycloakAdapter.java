package io.github.joaosimsic.infrastructure.adapters.output.idp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.joaosimsic.core.domain.AuthTokens;
import io.github.joaosimsic.core.domain.AuthUser;
import io.github.joaosimsic.core.exceptions.business.AuthenticationException;
import io.github.joaosimsic.core.exceptions.business.UserAlreadyExistsException;
import io.github.joaosimsic.core.ports.output.AuthPort;
import io.github.joaosimsic.infrastructure.config.properties.KeycloakProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.ws.rs.core.Response;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
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
  private final CacheManager cacheManager;
  private final WebClient webClient = WebClient.builder().build();

  @Override
  public AuthUser createUser(String name, String email, String password) {
    RealmResource realmResource = keycloakAdminClient.realm(keycloakProperties.realm());
    UsersResource usersResource = realmResource.users();

    UserRepresentation user = new UserRepresentation();
    user.setUsername(email);
    user.setEmail(email);
    user.setEnabled(true);
    user.setEmailVerified(false);
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
    String tokenUrl = getOidcEndpoint("token");

    MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
    formData.add("grant_type", "password");
    formData.add("client_id", keycloakProperties.clientId());
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
    String tokenUrl = getOidcEndpoint("token");

    MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
    formData.add("grant_type", "refresh_token");
    formData.add("client_id", keycloakProperties.clientId());
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
    String revokeUrl = getOidcEndpoint("revoke");

    MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
    formData.add("client_id", keycloakProperties.clientId());
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
  @CircuitBreaker(name = "authProvider")
  @Retryable(
      retryFor = {WebClientResponseException.class, Exception.class},
      maxAttemptsExpression = "${app.idp.retry.max-attempts:3}",
      backoff = @Backoff(delayExpression = "${app.idp.retry.initial-backoff-ms:1000}"))
  @Cacheable(value = "authUsers", key = "#accessToken", unless = "#result == null")
  public AuthUser getUserInfo(String accessToken) {
    String userInfoUrl = getOidcEndpoint("userinfo");

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
      throw e;
    }
  }

  @Recover
  public AuthUser recoverUserInfo(Exception e, String accessToken) {
    log.warn("Keycloak unreachable. Falling back to cache for token. Error: {}", e.getMessage());
    return getFromCache("authUsers", accessToken, AuthUser.class);
  }

  @Override
  public AuthUser parseIdToken(String idToken) {
    try {
      // JWT format: header.payload.signature
      String[] parts = idToken.split("\\.");
      if (parts.length != 3) {
        throw new AuthenticationException("Invalid ID token format");
      }

      // Decode the payload (second part)
      String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
      ObjectMapper mapper = new ObjectMapper();
      Map<String, Object> claims = mapper.readValue(payload, new TypeReference<>() {});

      // Try to get name from various claims (GitHub users may not have 'name' claim set)
      String name = (String) claims.get("name");
      if (name == null || name.isBlank()) {
        // Fallback: try given_name + family_name
        String givenName = (String) claims.get("given_name");
        String familyName = (String) claims.get("family_name");
        if (givenName != null || familyName != null) {
          name = ((givenName != null ? givenName : "") + " " + (familyName != null ? familyName : "")).trim();
        }
      }
      if (name == null || name.isBlank()) {
        // Fallback: use preferred_username (typically GitHub username)
        name = (String) claims.get("preferred_username");
      }
      if (name == null || name.isBlank()) {
        // Last resort: use email prefix
        String email = (String) claims.get("email");
        if (email != null && email.contains("@")) {
          name = email.substring(0, email.indexOf("@"));
        }
      }

      log.debug("Parsed ID token - sub: {}, email: {}, name: {}", 
          claims.get("sub"), claims.get("email"), name);

      return AuthUser.builder()
          .id((String) claims.get("sub"))
          .email((String) claims.get("email"))
          .name(name)
          .emailVerified(Boolean.TRUE.equals(claims.get("email_verified")))
          .build();
    } catch (Exception e) {
      log.error("Failed to parse ID token: {}", e.getMessage());
      throw new AuthenticationException("Failed to parse ID token");
    }
  }

  @Override
  @CacheEvict(value = "authUsers", allEntries = true)
  public void updateEmail(String userId, String newEmail) {
    log.info("Updating email for user {} in Keycloak", userId);

    RealmResource realmResource = keycloakAdminClient.realm(keycloakProperties.realm());
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
  @CacheEvict(value = "authUsers", allEntries = true)
  public void updatePassword(String userId, String currentPassword, String newPassword) {
    log.info("Updating password for user {} in Keycloak", userId);

    RealmResource realmResource = keycloakAdminClient.realm(keycloakProperties.realm());
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

  @Override
  public String getGitHubAuthUrl(String redirectUri, String state) {
    // Use external URL for browser redirects (user-facing OAuth flow)
    return UriComponentsBuilder.fromUriString(keycloakProperties.getExternalUrl())
        .path("/realms/" + keycloakProperties.realm() + "/protocol/openid-connect/auth")
        .queryParam("client_id", keycloakProperties.clientId())
        .queryParam("redirect_uri", redirectUri)
        .queryParam("response_type", "code")
        .queryParam("scope", "openid email profile")
        .queryParam("kc_idp_hint", "github")
        .queryParam("state", state)
        .toUriString();
  }

  @Override
  public AuthTokens exchangeCodeForTokens(String code, String redirectUri) {
    String tokenUrl = getOidcEndpoint("token");

    MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
    formData.add("grant_type", "authorization_code");
    formData.add("client_id", keycloakProperties.clientId());
    formData.add("code", code);
    formData.add("redirect_uri", redirectUri);

    try {
      log.debug("Exchanging code for tokens at: {}", tokenUrl);
      Map<String, Object> response =
          webClient
              .post()
              .uri(tokenUrl)
              .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
              .body(BodyInserters.fromFormData(formData))
              .retrieve()
              .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
              .block();

      log.debug("Token exchange response keys: {}", response != null ? response.keySet() : "null");
      return mapToAuthTokens(response);
    } catch (WebClientResponseException e) {
      log.error("Code exchange failed. Status: {}, Body: {}", e.getStatusCode(), e.getResponseBodyAsString());
      throw new AuthenticationException("Failed to exchange authorization code");
    }
  }

  private String getOidcEndpoint(String endpoint) {
    return String.format(
        "%s/realms/%s/protocol/openid-connect/%s",
        keycloakProperties.serverUrl(), keycloakProperties.realm(), endpoint);
  }

  private void verifyCurrentPassword(String email, String currentPassword) {
    try {
      login(email, currentPassword);
    } catch (AuthenticationException e) {
      throw new AuthenticationException("Current password is incorrect");
    }
  }

  private <T> T getFromCache(String cacheName, Object key, Class<T> type) {
    Cache cache = cacheManager.getCache(cacheName);
    if (cache == null) {
      log.warn("No cache found for {}, returning null", cacheName);
      return null;
    }
    return cache.get(key, type);
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
}
