package io.github.joaosimsic.infrastructure.adapters.output.idp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.joaosimsic.core.domain.AuthTokens;
import io.github.joaosimsic.core.domain.AuthUser;
import io.github.joaosimsic.core.exceptions.business.AuthenticationException;
import io.github.joaosimsic.core.exceptions.business.UserAlreadyExistsException;
import io.github.joaosimsic.core.ports.output.AuthPort;
import io.github.joaosimsic.infrastructure.config.properties.CognitoProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class CognitoAdapter implements AuthPort {

  private final CognitoIdentityProviderClient cognitoClient;
  private final CognitoProperties cognitoProperties;
  private final CacheManager cacheManager;
  private final WebClient webClient = WebClient.builder().build();

  @Override
  public AuthUser createUser(String name, String email, String password) {
    try {
      AdminCreateUserRequest userRequest =
          AdminCreateUserRequest.builder()
              .userPoolId(cognitoProperties.userPoolId())
              .username(email)
              .userAttributes(
                  AttributeType.builder().name("email").value(email).build(),
                  AttributeType.builder().name("name").value(name).build(),
                  AttributeType.builder().name("email_verified").value("false").build())
              .temporaryPassword(password)
              .messageAction(MessageActionType.SUPPRESS)
              .build();

      AdminCreateUserResponse response = cognitoClient.adminCreateUser(userRequest);
      String userId =
          response.user().attributes().stream()
              .filter(a -> a.name().equals("sub"))
              .findFirst()
              .map(AttributeType::value)
              .orElseThrow(() -> new AuthenticationException("Could not extract user ID"));

      AdminSetUserPasswordRequest passwordRequest =
          AdminSetUserPasswordRequest.builder()
              .userPoolId(cognitoProperties.userPoolId())
              .username(email)
              .password(password)
              .permanent(true)
              .build();
      cognitoClient.adminSetUserPassword(passwordRequest);

      log.info("User created successfully in Cognito with ID: {}", userId);
      return AuthUser.builder().id(userId).email(email).name(name).emailVerified(true).build();

    } catch (UsernameExistsException e) {
      throw new UserAlreadyExistsException("User with email " + email + " already exists");
    } catch (CognitoIdentityProviderException e) {
      log.error("Failed to create user in Cognito: {}", e.awsErrorDetails().errorMessage());
      throw new AuthenticationException("Failed to create user: " + e.getMessage());
    }
  }

  @Override
  public AuthTokens login(String email, String password) {
    try {
      AdminInitiateAuthRequest authRequest =
          AdminInitiateAuthRequest.builder()
              .userPoolId(cognitoProperties.userPoolId())
              .clientId(cognitoProperties.clientId())
              .authFlow(AuthFlowType.ADMIN_NO_SRP_AUTH)
              .authParameters(Map.of("USERNAME", email, "PASSWORD", password))
              .build();

      AdminInitiateAuthResponse response = cognitoClient.adminInitiateAuth(authRequest);
      return mapToAuthTokens(response.authenticationResult());

    } catch (NotAuthorizedException | UserNotFoundException e) {
      log.error("Login failed for user: {}. Reason: {}", email, e.getMessage());
      throw new AuthenticationException("Invalid credentials");
    }
  }

  @Override
  public AuthTokens refreshToken(String refreshToken) {
    try {
      AdminInitiateAuthRequest authRequest =
          AdminInitiateAuthRequest.builder()
              .userPoolId(cognitoProperties.userPoolId())
              .clientId(cognitoProperties.clientId())
              .authFlow(AuthFlowType.REFRESH_TOKEN_AUTH)
              .authParameters(Map.of("REFRESH_TOKEN", refreshToken))
              .build();

      AdminInitiateAuthResponse response = cognitoClient.adminInitiateAuth(authRequest);
      return mapToAuthTokens(response.authenticationResult());
    } catch (CognitoIdentityProviderException e) {
      throw new AuthenticationException("Failed to refresh token");
    }
  }

  @Override
  public void logout(String accessToken) {
    try {
      GlobalSignOutRequest signOutRequest =
          GlobalSignOutRequest.builder().accessToken(accessToken).build();
      cognitoClient.globalSignOut(signOutRequest);
      log.info("User logged out successfully from Cognito");
    } catch (CognitoIdentityProviderException e) {
      log.warn("Logout request failed in Cognito: {}", e.getMessage());
    }
  }

  @Override
  @CircuitBreaker(name = "authProvider")
  @Retryable(
      retryFor = {CognitoIdentityProviderException.class},
      maxAttemptsExpression = "${app.idp.retry.max-attempts:3}",
      backoff = @Backoff(delayExpression = "${app.idp.retry.initial-backoff-ms:1000}"))
  @Cacheable(value = "authUsers", key = "#accessToken", unless = "#result == null")
  public AuthUser getUserInfo(String accessToken) {
    try {
      GetUserRequest getUserRequest = GetUserRequest.builder().accessToken(accessToken).build();

      GetUserResponse response = cognitoClient.getUser(getUserRequest);
      Map<String, String> attributes =
          response.userAttributes().stream()
              .collect(Collectors.toMap(AttributeType::name, AttributeType::value));

      return AuthUser.builder()
          .id(attributes.get("sub"))
          .email(attributes.get("email"))
          .name(attributes.get("name"))
          .emailVerified("true".equals(attributes.get("email_verified")))
          .build();
    } catch (CognitoIdentityProviderException e) {
      log.error("Cognito communication failed: {}", e.getMessage());
      throw e;
    }
  }

  @Recover
  public AuthUser recoverUserInfo(Exception e, String accessToken) {
    log.warn("IdP Failure. Falling back to cache for token. Error: {}", e.getMessage());
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
        // Fallback: use preferred_username or cognito:username
        name = (String) claims.get("preferred_username");
        if (name == null) {
          name = (String) claims.get("cognito:username");
        }
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
  public String getGitHubAuthUrl(String redirectUri, String state) {
    return String.format(
        "%s/oauth2/authorize?identity_provider=GitHub&client_id=%s&response_type=code&redirect_uri=%s&state=%s",
        cognitoProperties.domainUrl(), cognitoProperties.clientId(), redirectUri, state);
  }

  @Override
  public AuthTokens exchangeCodeForTokens(String code, String redirectUri) {
    String tokenUrl = cognitoProperties.domainUrl() + "/oauth2/token";

    MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
    formData.add("grant_type", "authorization_code");
    formData.add("client_id", cognitoProperties.clientId());
    formData.add("client_secret", cognitoProperties.clientSecret());
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

      return AuthTokens.builder()
          .accessToken((String) response.get("access_token"))
          .refreshToken((String) response.get("refresh_token"))
          .idToken((String) response.get("id_token"))
          .expiresIn((Integer) response.get("expires_in"))
          .refreshExpiresIn(0)
          .build();
    } catch (WebClientResponseException e) {
      log.error("Code exchange failed. Status: {}", e.getStatusCode());
      throw new AuthenticationException("Failed to exchange authorization code");
    }
  }

  @Override
  @CacheEvict(value = "authUsers", allEntries = true)
  public void updateEmail(String userId, String newEmail) {
    log.info("Updating email for user {} in Cognito", userId);

    try {
      AdminUpdateUserAttributesRequest request =
          AdminUpdateUserAttributesRequest.builder()
              .userPoolId(cognitoProperties.userPoolId())
              .username(userId)
              .userAttributes(
                  AttributeType.builder().name("email").value(newEmail).build(),
                  AttributeType.builder().name("email_verified").value("true").build())
              .build();

      cognitoClient.adminUpdateUserAttributes(request);

      log.info("Email updated successfully for user {} in Cognito", userId);
    } catch (CognitoIdentityProviderException e) {
      log.error("Failed to update email for user {} in Cognito: {}", userId, e.getMessage());
      throw new AuthenticationException("Failed to update email: " + e.getMessage());
    }
  }

  @Override
  @CacheEvict(value = "authUsers", allEntries = true)
  public void updatePassword(String userId, String currentPassword, String newPassword) {
    log.info("Updating password for user {} in Cognito", userId);

    try {
      GetUserRequest getUserRequest =
          GetUserRequest.builder()
              .accessToken(getAccessTokenForUser(userId, currentPassword))
              .build();
      cognitoClient.getUser(getUserRequest);

      AdminSetUserPasswordRequest passwordRequest =
          AdminSetUserPasswordRequest.builder()
              .userPoolId(cognitoProperties.userPoolId())
              .username(userId)
              .password(newPassword)
              .permanent(true)
              .build();

      cognitoClient.adminSetUserPassword(passwordRequest);

      log.info("Password updated successfully for user {} in Cognito", userId);
    } catch (NotAuthorizedException e) {
      throw new AuthenticationException("Current password is incorrect");
    } catch (CognitoIdentityProviderException e) {
      log.error("Failed to update password for user {} in Cognito: {}", userId, e.getMessage());
      throw new AuthenticationException("Failed to update password: " + e.getMessage());
    }
  }

  private String getAccessTokenForUser(String userId, String password) {
    try {
      AdminGetUserRequest getUserRequest =
          AdminGetUserRequest.builder()
              .userPoolId(cognitoProperties.userPoolId())
              .username(userId)
              .build();

      AdminGetUserResponse userResponse = cognitoClient.adminGetUser(getUserRequest);
      String email =
          userResponse.userAttributes().stream()
              .filter(a -> a.name().equals("email"))
              .findFirst()
              .map(AttributeType::value)
              .orElseThrow(() -> new AuthenticationException("User email not found"));

      AuthTokens tokens = login(email, password);
      return tokens.getAccessToken();
    } catch (CognitoIdentityProviderException e) {
      throw new AuthenticationException("Failed to verify current password");
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

  private AuthTokens mapToAuthTokens(AuthenticationResultType result) {
    return AuthTokens.builder()
        .accessToken(result.accessToken())
        .refreshToken(result.refreshToken())
        .idToken(result.idToken())
        .expiresIn(result.expiresIn())
        .refreshExpiresIn(0)
        .build();
  }
}
