package io.github.joaosimsic.infrastructure.adapters.input.web.controllers;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.github.joaosimsic.infrastructure.BaseIntegrationTest;
import io.github.joaosimsic.infrastructure.adapters.input.web.requests.UserRequest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class UserControllerIT extends BaseIntegrationTest {

  @Test
  @DisplayName("Should create an user and persist it")
  void shouldCreateUserSuccessfully() {
    UserRequest request = new UserRequest("Test User", "test@example.com", "password");

    given()
        .contentType(ContentType.JSON)
        .body(request)
        .when()
        .post("/api/users")
        .then()
        .statusCode(HttpStatus.CREATED.value())
        .header("Location", containsString("/api/users/"))
        .body("id", notNullValue())
        .body("name", equalTo("Test User"))
        .body("email", equalTo("test@example.com"))
        .body("password", nullValue());
  }

  @Test
  @DisplayName("Should return 409 Conflict when email already exists")
  void shouldReturnConflictForDuplicateEmail() {
    UserRequest request = new UserRequest("First", "email@example.com", "password");

    given().contentType(ContentType.JSON).body(request).post("/api/users");

    given()
        .contentType(ContentType.JSON)
        .body(request)
        .when()
        .post("/api/users")
        .then()
        .statusCode(HttpStatus.CONFLICT.value())
        .body("error", equalTo("Data Conflict"))
        .body("message", containsString("already exists"));
  }
}
