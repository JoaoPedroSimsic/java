package io.github.joaosimsic.infrastructure.adapters.output.idp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.joaosimsic.core.domain.AuthTokens;
import io.github.joaosimsic.core.domain.AuthUser;
import io.github.joaosimsic.core.exceptions.business.AuthenticationException;
import io.github.joaosimsic.core.exceptions.business.UserAlreadyExistsException;
import io.github.joaosimsic.infrastructure.config.properties.CognitoProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GetUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;

@ExtendWith(MockitoExtension.class)
class CognitoAdapterTest {

  @Mock private CognitoIdentityProviderClient cognitoClient;

  private CognitoProperties cognitoProperties;

  private CognitoAdapter cognitoAdapter;

  @BeforeEach
  void setUp() {
    cognitoProperties = new CognitoProperties();
    cognitoProperties.setUserPoolId("us-east-1_testPool");
    cognitoProperties.setClientId("test-client-id");
    cognitoProperties.setClientSecret("test-client-secret");
    cognitoProperties.setDomainUrl("https://test.auth.us-east-1.amazoncognito.com");
    cognitoProperties.setRegion("us-east-1");

    cognitoAdapter = new CognitoAdapter(cognitoClient, cognitoProperties);
  }

  @Nested
  @DisplayName("createUser")
  class CreateUser {

    @Test
    @DisplayName("should correctly map AdminCreateUserResponse to AuthUser")
    void shouldCorrectlyMapAdminCreateUserResponseToAuthUser() {
      String name = "John Doe";
      String email = "john@example.com";
      String password = "password123";

      UserType userType =
          UserType.builder()
              .attributes(
                  AttributeType.builder().name("sub").value("user-uuid-123").build(),
                  AttributeType.builder().name("email").value(email).build(),
                  AttributeType.builder().name("name").value(name).build(),
                  AttributeType.builder().name("email_verified").value("true").build())
              .build();

      AdminCreateUserResponse createResponse =
          AdminCreateUserResponse.builder().user(userType).build();

      when(cognitoClient.adminCreateUser(any(AdminCreateUserRequest.class)))
          .thenReturn(createResponse);

      AuthUser result = cognitoAdapter.createUser(name, email, password);

      assertNotNull(result);
      assertEquals("user-uuid-123", result.getId());
      assertEquals(email, result.getEmail());
      assertEquals(name, result.getName());
      assertTrue(result.isEmailVerified());
    }

    @Test
    @DisplayName("should create user with correct attributes in Cognito")
    void shouldCreateUserWithCorrectAttributesInCognito() {
      String name = "John Doe";
      String email = "john@example.com";
      String password = "password123";

      UserType userType =
          UserType.builder()
              .attributes(AttributeType.builder().name("sub").value("user-uuid-123").build())
              .build();

      AdminCreateUserResponse createResponse =
          AdminCreateUserResponse.builder().user(userType).build();

      when(cognitoClient.adminCreateUser(any(AdminCreateUserRequest.class)))
          .thenReturn(createResponse);

      cognitoAdapter.createUser(name, email, password);

      ArgumentCaptor<AdminCreateUserRequest> requestCaptor =
          ArgumentCaptor.forClass(AdminCreateUserRequest.class);
      verify(cognitoClient).adminCreateUser(requestCaptor.capture());

      AdminCreateUserRequest capturedRequest = requestCaptor.getValue();
      assertEquals("us-east-1_testPool", capturedRequest.userPoolId());
      assertEquals(email, capturedRequest.username());

      List<AttributeType> attributes = capturedRequest.userAttributes();
      assertTrue(
          attributes.stream()
              .anyMatch(a -> a.name().equals("email") && a.value().equals(email)));
      assertTrue(
          attributes.stream()
              .anyMatch(a -> a.name().equals("name") && a.value().equals(name)));
      assertTrue(
          attributes.stream()
              .anyMatch(a -> a.name().equals("email_verified") && a.value().equals("true")));
    }

    @Test
    @DisplayName("should set permanent password after user creation")
    void shouldSetPermanentPasswordAfterUserCreation() {
      String name = "John Doe";
      String email = "john@example.com";
      String password = "password123";

      UserType userType =
          UserType.builder()
              .attributes(AttributeType.builder().name("sub").value("user-uuid-123").build())
              .build();

      AdminCreateUserResponse createResponse =
          AdminCreateUserResponse.builder().user(userType).build();

      when(cognitoClient.adminCreateUser(any(AdminCreateUserRequest.class)))
          .thenReturn(createResponse);

      cognitoAdapter.createUser(name, email, password);

      ArgumentCaptor<AdminSetUserPasswordRequest> passwordCaptor =
          ArgumentCaptor.forClass(AdminSetUserPasswordRequest.class);
      verify(cognitoClient).adminSetUserPassword(passwordCaptor.capture());

      AdminSetUserPasswordRequest capturedRequest = passwordCaptor.getValue();
      assertEquals("us-east-1_testPool", capturedRequest.userPoolId());
      assertEquals(email, capturedRequest.username());
      assertEquals(password, capturedRequest.password());
      assertTrue(capturedRequest.permanent());
    }

    @Test
    @DisplayName("should throw UserAlreadyExistsException when UsernameExistsException occurs")
    void shouldThrowUserAlreadyExistsExceptionWhenUsernameExists() {
      String email = "existing@example.com";

      when(cognitoClient.adminCreateUser(any(AdminCreateUserRequest.class)))
          .thenThrow(UsernameExistsException.builder().message("User exists").build());

      UserAlreadyExistsException exception =
          assertThrows(
              UserAlreadyExistsException.class,
              () -> cognitoAdapter.createUser("John", email, "password123"));

      assertTrue(exception.getMessage().contains(email));
      assertTrue(exception.getMessage().contains("already exists"));
    }
  }

  @Nested
  @DisplayName("login")
  class Login {

    @Test
    @DisplayName("should return AuthTokens on successful login")
    void shouldReturnAuthTokensOnSuccessfulLogin() {
      String email = "john@example.com";
      String password = "password123";

      AuthenticationResultType authResult =
          AuthenticationResultType.builder()
              .accessToken("access-token-value")
              .refreshToken("refresh-token-value")
              .idToken("id-token-value")
              .expiresIn(3600)
              .build();

      AdminInitiateAuthResponse authResponse =
          AdminInitiateAuthResponse.builder().authenticationResult(authResult).build();

      when(cognitoClient.adminInitiateAuth(any(AdminInitiateAuthRequest.class)))
          .thenReturn(authResponse);

      AuthTokens result = cognitoAdapter.login(email, password);

      assertNotNull(result);
      assertEquals("access-token-value", result.getAccessToken());
      assertEquals("refresh-token-value", result.getRefreshToken());
      assertEquals("id-token-value", result.getIdToken());
      assertEquals(3600, result.getExpiresIn());
    }

    @Test
    @DisplayName("should throw AuthenticationException on NotAuthorizedException")
    void shouldThrowAuthenticationExceptionOnNotAuthorized() {
      when(cognitoClient.adminInitiateAuth(any(AdminInitiateAuthRequest.class)))
          .thenThrow(NotAuthorizedException.builder().message("Incorrect credentials").build());

      AuthenticationException exception =
          assertThrows(
              AuthenticationException.class,
              () -> cognitoAdapter.login("john@example.com", "wrongpassword"));

      assertEquals("Invalid credentials", exception.getMessage());
    }

    @Test
    @DisplayName("should throw AuthenticationException on UserNotFoundException")
    void shouldThrowAuthenticationExceptionOnUserNotFound() {
      when(cognitoClient.adminInitiateAuth(any(AdminInitiateAuthRequest.class)))
          .thenThrow(UserNotFoundException.builder().message("User not found").build());

      AuthenticationException exception =
          assertThrows(
              AuthenticationException.class,
              () -> cognitoAdapter.login("notfound@example.com", "password123"));

      assertEquals("Invalid credentials", exception.getMessage());
    }
  }

  @Nested
  @DisplayName("refreshToken")
  class RefreshToken {

    @Test
    @DisplayName("should return new AuthTokens on successful refresh")
    void shouldReturnNewAuthTokensOnSuccessfulRefresh() {
      String refreshToken = "old-refresh-token";

      AuthenticationResultType authResult =
          AuthenticationResultType.builder()
              .accessToken("new-access-token")
              .refreshToken("new-refresh-token")
              .idToken("new-id-token")
              .expiresIn(3600)
              .build();

      AdminInitiateAuthResponse authResponse =
          AdminInitiateAuthResponse.builder().authenticationResult(authResult).build();

      when(cognitoClient.adminInitiateAuth(any(AdminInitiateAuthRequest.class)))
          .thenReturn(authResponse);

      AuthTokens result = cognitoAdapter.refreshToken(refreshToken);

      assertNotNull(result);
      assertEquals("new-access-token", result.getAccessToken());
    }
  }

  @Nested
  @DisplayName("getUserInfo")
  class GetUserInfo {

    @Test
    @DisplayName("should correctly map GetUserResponse to AuthUser")
    void shouldCorrectlyMapGetUserResponseToAuthUser() {
      String accessToken = "valid-access-token";

      GetUserResponse getUserResponse =
          GetUserResponse.builder()
              .userAttributes(
                  AttributeType.builder().name("sub").value("user-123").build(),
                  AttributeType.builder().name("email").value("john@example.com").build(),
                  AttributeType.builder().name("name").value("John Doe").build(),
                  AttributeType.builder().name("email_verified").value("true").build())
              .build();

      when(cognitoClient.getUser(any(GetUserRequest.class))).thenReturn(getUserResponse);

      AuthUser result = cognitoAdapter.getUserInfo(accessToken);

      assertNotNull(result);
      assertEquals("user-123", result.getId());
      assertEquals("john@example.com", result.getEmail());
      assertEquals("John Doe", result.getName());
      assertTrue(result.isEmailVerified());
    }
  }

  @Nested
  @DisplayName("getGitHubAuthUrl")
  class GetGitHubAuthUrl {

    @Test
    @DisplayName("should generate correct GitHub auth URL with Cognito domain")
    void shouldGenerateCorrectGitHubAuthUrl() {
      String redirectUri = "http://localhost:3000/callback";
      String state = "random-state-123";

      String result = cognitoAdapter.getGitHubAuthUrl(redirectUri, state);

      assertTrue(result.contains(cognitoProperties.getDomainUrl()));
      assertTrue(result.contains("identity_provider=GitHub"));
      assertTrue(result.contains("client_id=" + cognitoProperties.getClientId()));
      assertTrue(result.contains("redirect_uri=" + redirectUri));
      assertTrue(result.contains("state=" + state));
      assertTrue(result.contains("response_type=code"));
    }
  }
}
