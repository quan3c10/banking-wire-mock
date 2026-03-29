package com.example.wiremock;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@DisplayName("Authentication Flow")
class AuthenticationTest extends BaseWireMockTest {

    @Test
    @DisplayName("POST /auth/login - Valid credentials return 200 with JWT tokens")
    void loginWithValidCredentialsShouldReturn200WithTokens() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", "john.doe", "password", "SecureP@ss123"))
        .when()
            .post("/api/v1/auth/login")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("accessToken", notNullValue())
            .body("refreshToken", notNullValue())
            .body("tokenType", equalTo("Bearer"))
            .body("expiresIn", equalTo(3600))
            .body("user.userId", equalTo("user-001"))
            .body("user.username", equalTo("john.doe"))
            .body("user.role", equalTo("CUSTOMER"));
    }

    @Test
    @DisplayName("POST /auth/login - Invalid credentials return 401")
    void loginWithInvalidCredentialsShouldReturn401() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", "unknown.user", "password", "wrongpassword"))
        .when()
            .post("/api/v1/auth/login")
        .then()
            .statusCode(401)
            .contentType(ContentType.JSON)
            .body("error", equalTo("INVALID_CREDENTIALS"))
            .body("message", notNullValue())
            .body("timestamp", notNullValue());
    }

    @Test
    @DisplayName("POST /auth/login - Outage trigger username returns 503 with Retry-After header")
    void loginWithOutageTriggerShouldReturn503() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", "trigger.outage", "password", "anypassword"))
        .when()
            .post("/api/v1/auth/login")
        .then()
            .statusCode(503)
            .header("Retry-After", equalTo("30"))
            .body("error", equalTo("SERVICE_UNAVAILABLE"))
            .body("retryAfter", equalTo(30));
    }

    @Test
    @DisplayName("POST /auth/refresh - Valid refresh token returns new JWT")
    void refreshTokenShouldReturnNewJwt() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("refreshToken", "rt_mock_refresh_token_abc123"))
        .when()
            .post("/api/v1/auth/refresh")
        .then()
            .statusCode(200)
            .body("accessToken", notNullValue())
            .body("refreshToken", notNullValue())
            .body("tokenType", equalTo("Bearer"))
            .body("expiresIn", equalTo(3600));
    }

    @Test
    @DisplayName("POST /auth/logout - Valid Bearer token returns 200")
    void logoutWithValidTokenShouldReturn200() {
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + VALID_JWT)
        .when()
            .post("/api/v1/auth/logout")
        .then()
            .statusCode(200)
            .body("message", containsString("invalidated"))
            .body("loggedOutAt", notNullValue());
    }

    @Test
    @DisplayName("GET /auth/me - Valid Bearer token returns user profile")
    void getMeWithValidTokenShouldReturnUserProfile() {
        given()
            .header("Authorization", "Bearer " + VALID_JWT)
        .when()
            .get("/api/v1/auth/me")
        .then()
            .statusCode(200)
            .body("userId", equalTo("user-001"))
            .body("username", equalTo("john.doe"))
            .body("email", notNullValue())
            .body("role", equalTo("CUSTOMER"));
    }

    @Test
    @DisplayName("GET /auth/me - Missing Authorization header returns 401")
    void getMeWithoutTokenShouldReturn401() {
        given()
        .when()
            .get("/api/v1/auth/me")
        .then()
            .statusCode(401)
            .body("error", equalTo("UNAUTHORIZED"))
            .body("message", notNullValue());
    }

    @Test
    @DisplayName("GET /auth/me - Invalid token format returns 401")
    void getMeWithInvalidTokenShouldReturn401() {
        given()
            .header("Authorization", "Bearer invalid-token")
        .when()
            .get("/api/v1/auth/me")
        .then()
            .statusCode(401);
    }
}
