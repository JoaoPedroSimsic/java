package io.github.joaosimsic.infrastructure.adapters.output.idp;

import io.github.joaosimsic.core.domain.AuthTokens;
import io.github.joaosimsic.core.domain.AuthUser;
import io.github.joaosimsic.core.exceptions.business.AuthenticationException;
import io.github.joaosimsic.core.exceptions.business.UserAlreadyExistsException;
import io.github.joaosimsic.core.ports.output.AuthPort;
import io.github.joaosimsic.infrastructure.config.properties.CognitoProperties;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class CognitoAdapter implements AuthPort {

  private final CognitoIdentityProviderClient cognitoClient;
  private final CognitoProperties cognitoProperties;
  private final WebClient webClient = WebClient.builder().build();

  @Override
  public AuthUser createUser(String name, String email, String password) {
    try {
      AdminCreateUserRequest userRequest =
          AdminCreateUserRequest.builder()
              .userPoolId(cognitoProperties.getUserPoolId())
              .username(email)
              .userAttributes(
                  AttributeType.builder().name("email").value(email).build(),
                  AttributeType.builder().name("name").value(name).build(),
                  AttributeType.builder().name("email_verified").value("true").build())
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
              .userPoolId(cognitoProperties.getUserPoolId())
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
              .userPoolId(cognitoProperties.getUserPoolId())
              .clientId(cognitoProperties.getClientId())
              .authFlow(AuthFlowType.ADMIN_NO_SRP_AUTH)
              .authParameters(
                  Map.of(
                      "USERNAME", email,
                      "PASSWORD", password))
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
              .userPoolId(cognitoProperties.getUserPoolId())
              .clientId(cognitoProperties.getClientId())
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
      throw new AuthenticationException("Failed to get user info");
    }
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

  @Override
  public String getGitHubAuthUrl(String redirectUri, String state) {
    return String.format(
        "%s/oauth2/authorize?identity_provider=GitHub&client_id=%s&response_type=code&redirect_uri=%s&state=%s",
        cognitoProperties.getDomainUrl(), cognitoProperties.getClientId(), redirectUri, state);
  }

  @Override
  public AuthTokens exchangeCodeForTokens(String code, String redirectUri) {
    String tokenUrl = cognitoProperties.getDomainUrl() + "/oauth2/token";

    MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
    formData.add("grant_type", "authorization_code");
    formData.add("client_id", cognitoProperties.getClientId());
    formData.add("client_secret", cognitoProperties.getClientSecret());
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
}
