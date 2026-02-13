package io.github.joaosimsic.infrastructure.adapters.input.web.controllers;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.github.joaosimsic.infrastructure.BaseIntegrationTest;
import io.github.joaosimsic.infrastructure.adapters.input.web.requests.UserRequest;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;

class UserControllerIT extends BaseIntegrationTest {

  @Value("${app.jwt.secret}")
  private String jwtSecret;

  private RequestSpecification spec;

  @BeforeEach
  void setup() {
    this.spec =
        new RequestSpecBuilder()
            .addHeader("X-Gateway-Secret", jwtSecret)
            .addHeader("X-User-Id", "test-user-id")
            .addHeader("X-User-Email", "test@example.com")
            .setContentType(ContentType.JSON)
            .build();
  }

  @Test
  @DisplayName("Should create an user and persist it")
  void shouldCreateUserSuccessfully() {
    String uniqueEmail = "test-" + System.nanoTime() + "@example.com";
    var request = new UserRequest("Test User", uniqueEmail, "password");

    given()
        .spec(spec)
        .body(request)
        .when()
        .post("/api/users")
        .then()
        .statusCode(HttpStatus.CREATED.value())
        .header("Location", containsString("/api/users/"))
        .body("id", notNullValue())
        .body("name", is(request.name()))
        .body("email", is(request.email()))
        .body("password", nullValue());
  }

  @ParameterizedTest
  @ValueSource(strings = {"invalid-email", "test@", "@domain.com", ""})
  @DisplayName("Should return 400 for various invalid email formats")
  void shouldReturn400WhenEmailIsInvalid(String invalidEmail) {
    var request = new UserRequest("Valid Name", invalidEmail, "password123");

    given()
        .spec(spec)
        .body(request)
        .when()
        .post("/api/users")
        .then()
        .statusCode(HttpStatus.BAD_REQUEST.value());
  }
}
