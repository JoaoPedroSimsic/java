package com.example.message.infrastructure.adapters.input.web.controllers;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import com.example.message.infrastructure.BaseIntegrationTest;
import com.example.message.infrastructure.adapters.input.web.requests.LoginRequest;
import com.example.message.infrastructure.adapters.input.web.requests.UserRequest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AuthControllerIT extends BaseIntegrationTest {
  private String testEmail;

  @BeforeEach
  void setupUser() {
    testEmail = "auth" + System.nanoTime() + "@example.com";
    UserRequest signup = new UserRequest("Auth User", testEmail, "password");
    given().contentType(ContentType.JSON).body(signup).post("/api/users");
  }

  @Test
  @DisplayName("Should login and set HttpOnly JWT cookie")
  void shouldLoginSuccessfully() {
    LoginRequest loginRequest = new LoginRequest(testEmail, "password");

    given()
        .contentType(ContentType.JSON)
        .body(loginRequest)
        .when()
        .post("/api/auth/login")
        .then()
        .statusCode(HttpStatus.OK.value())
        .cookie("jwt", notNullValue());
  }

  @Test
  @DisplayName("Should access protected resource with JWT cookie")
  void shouldAccessProtectedResourceWithToken() {
    LoginRequest loginRequest = new LoginRequest(testEmail, "password");

    var response =
        given()
            .contentType(ContentType.JSON)
            .body(loginRequest)
            .post("/api/auth/login")
            .then()
            .statusCode(HttpStatus.OK.value())
            .extract()
            .response();

    String jwtCookie = response.getCookie("jwt");

    given()
        .cookie("jwt", jwtCookie)
        .when()
        .get("/api/users")
        .then()
        .statusCode(HttpStatus.OK.value());
  }

  @Test
  @DisplayName("Should return 403 Forbidden when accessing protected " + "resource without cookie")
  void shouldFailWithoutToken() {
    given().when().get("/api/users").then().statusCode(HttpStatus.FORBIDDEN.value());
  }
}
