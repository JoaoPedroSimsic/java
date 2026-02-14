package io.github.joaosimsic.infrastructure.adapters.output.idp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.joaosimsic.core.domain.AuthUser;
import io.github.joaosimsic.core.exceptions.business.UserAlreadyExistsException;
import io.github.joaosimsic.infrastructure.config.properties.KeycloakProperties;
import jakarta.ws.rs.core.Response;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KeycloakAdapterTest {

  @Mock private Keycloak keycloakAdminClient;
  @Mock private RealmResource realmResource;
  @Mock private UsersResource usersResource;
  @Mock private UserResource userResource;

  private KeycloakProperties keycloakProperties;
  private KeycloakAdapter keycloakAdapter;

  @BeforeEach
  void setUp() {
    keycloakProperties = new KeycloakProperties();
    keycloakProperties.setServerUrl("http://localhost:8080");
    keycloakProperties.setRealm("test-realm");
    keycloakProperties.setClientId("test-client");

    keycloakAdapter = new KeycloakAdapter(keycloakAdminClient, keycloakProperties);
  }

  @Nested
  @DisplayName("createUser")
  class CreateUser {

    @Test
    @DisplayName("should create user and return AuthUser with extracted ID")
    void shouldCreateUserAndReturnAuthUser() {
      String name = "John Doe";
      String email = "john@example.com";
      String password = "password123";
      String userId = "user-uuid-123";

      when(keycloakAdminClient.realm(anyString())).thenReturn(realmResource);
      when(realmResource.users()).thenReturn(usersResource);

      Response mockResponse = mock(Response.class);
      when(mockResponse.getStatus()).thenReturn(201);
      when(mockResponse.getHeaderString("Location"))
          .thenReturn("http://localhost:8080/admin/realms/test-realm/users/" + userId);
      when(usersResource.create(any(UserRepresentation.class))).thenReturn(mockResponse);

      AuthUser result = keycloakAdapter.createUser(name, email, password);

      assertNotNull(result);
      assertEquals(userId, result.getId());
      assertEquals(email, result.getEmail());
      assertEquals(name, result.getName());
      assertTrue(result.isEmailVerified());
    }

    @Test
    @DisplayName("should create user with correct UserRepresentation")
    void shouldCreateUserWithCorrectRepresentation() {
      String name = "John Doe";
      String email = "john@example.com";
      String password = "password123";

      when(keycloakAdminClient.realm(anyString())).thenReturn(realmResource);
      when(realmResource.users()).thenReturn(usersResource);

      Response mockResponse = mock(Response.class);
      when(mockResponse.getStatus()).thenReturn(201);
      when(mockResponse.getHeaderString("Location"))
          .thenReturn("http://localhost:8080/admin/realms/test-realm/users/user-123");
      when(usersResource.create(any(UserRepresentation.class))).thenReturn(mockResponse);

      keycloakAdapter.createUser(name, email, password);

      ArgumentCaptor<UserRepresentation> userCaptor =
          ArgumentCaptor.forClass(UserRepresentation.class);
      verify(usersResource).create(userCaptor.capture());

      UserRepresentation capturedUser = userCaptor.getValue();
      assertEquals(email, capturedUser.getUsername());
      assertEquals(email, capturedUser.getEmail());
      assertTrue(capturedUser.isEnabled());
      assertTrue(capturedUser.isEmailVerified());
      assertEquals(Collections.emptyList(), capturedUser.getRequiredActions());
      assertEquals(name, capturedUser.getAttributes().get("name").get(0));

      assertEquals(1, capturedUser.getCredentials().size());
      CredentialRepresentation credential = capturedUser.getCredentials().get(0);
      assertEquals(CredentialRepresentation.PASSWORD, credential.getType());
      assertEquals(password, credential.getValue());
      assertEquals(false, credential.isTemporary());
    }

    @Test
    @DisplayName("should throw UserAlreadyExistsException when user exists (409)")
    void shouldThrowUserAlreadyExistsExceptionWhenUserExists() {
      String email = "existing@example.com";

      when(keycloakAdminClient.realm(anyString())).thenReturn(realmResource);
      when(realmResource.users()).thenReturn(usersResource);

      Response mockResponse = mock(Response.class);
      when(mockResponse.getStatus()).thenReturn(409);
      when(usersResource.create(any(UserRepresentation.class))).thenReturn(mockResponse);

      UserAlreadyExistsException exception =
          assertThrows(
              UserAlreadyExistsException.class,
              () -> keycloakAdapter.createUser("John", email, "password123"));

      assertTrue(exception.getMessage().contains(email));
      assertTrue(exception.getMessage().contains("already exists"));
    }
  }

  @Nested
  @DisplayName("getGitHubAuthUrl")
  class GetGitHubAuthUrl {

    @Test
    @DisplayName("should generate correct GitHub auth URL with Keycloak")
    void shouldGenerateCorrectGitHubAuthUrl() {
      String redirectUri = "http://localhost:3000/callback";
      String state = "random-state-123";

      String result = keycloakAdapter.getGitHubAuthUrl(redirectUri, state);

      assertTrue(result.contains(keycloakProperties.getServerUrl()));
      assertTrue(result.contains("/realms/" + keycloakProperties.getRealm()));
      assertTrue(result.contains("/protocol/openid-connect/auth"));
      assertTrue(result.contains("client_id=" + keycloakProperties.getClientId()));
      assertTrue(result.contains("redirect_uri=" + redirectUri));
      assertTrue(result.contains("state=" + state));
      assertTrue(result.contains("response_type=code"));
      assertTrue(result.contains("kc_idp_hint=github"));
    }
  }

  @Nested
  @DisplayName("updateEmail")
  class UpdateEmail {

    @Test
    @DisplayName("should update user email in Keycloak")
    void shouldUpdateUserEmail() {
      String userId = "user-123";
      String newEmail = "newemail@example.com";

      when(keycloakAdminClient.realm(anyString())).thenReturn(realmResource);
      when(realmResource.users()).thenReturn(usersResource);
      when(usersResource.get(userId)).thenReturn(userResource);

      UserRepresentation existingUser = new UserRepresentation();
      existingUser.setEmail("oldemail@example.com");
      existingUser.setUsername("oldemail@example.com");
      when(userResource.toRepresentation()).thenReturn(existingUser);

      keycloakAdapter.updateEmail(userId, newEmail);

      ArgumentCaptor<UserRepresentation> userCaptor =
          ArgumentCaptor.forClass(UserRepresentation.class);
      verify(userResource).update(userCaptor.capture());

      UserRepresentation updatedUser = userCaptor.getValue();
      assertEquals(newEmail, updatedUser.getEmail());
      assertEquals(newEmail, updatedUser.getUsername());
    }
  }

}
