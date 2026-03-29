package com.example.wiremock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Tests security-relevant header matching:
 * - Authorization header presence/absence on protected endpoints
 * - X-Request-ID and X-Correlation-ID echo behavior
 */
@DisplayName("Security Header Matching")
class SecurityHeadersTest extends BaseWireMockTest {

    @Test
    @DisplayName("GET /accounts/{id}/balance - Missing Authorization returns 401")
    void missingAuthHeaderOnProtectedEndpointShouldReturn401() {
        given()
        .when()
            .get("/api/v1/accounts/ACC-001/balance")
        .then()
            .statusCode(401)
            .body("error", equalTo("UNAUTHORIZED"))
            .body("message", notNullValue());
    }

    @Test
    @DisplayName("GET /accounts/{id}/balance - Valid Bearer token returns 200")
    void validAuthHeaderOnProtectedEndpointShouldReturn200() {
        given()
            .header("Authorization", "Bearer " + VALID_JWT)
        .when()
            .get("/api/v1/accounts/ACC-001/balance")
        .then()
            .statusCode(200)
            .body("accountId", equalTo("ACC-001"));
    }

    @Test
    @DisplayName("GET /ping - X-Request-ID header is echoed back in response")
    void requestIdHeaderShouldBeEchoedInResponse() {
        String requestId = "req-" + System.currentTimeMillis();
        String correlationId = "corr-" + System.currentTimeMillis();

        given()
            .header("X-Request-ID", requestId)
            .header("X-Correlation-ID", correlationId)
        .when()
            .get("/api/v1/ping")
        .then()
            .statusCode(200)
            .header("X-Request-ID", equalTo(requestId))
            .header("X-Correlation-ID", equalTo(correlationId))
            .body("requestId", equalTo(requestId))
            .body("correlationId", equalTo(correlationId))
            .body("message", equalTo("pong"));
    }

    @Test
    @DisplayName("GET /auth/me - Valid JWT (Bearer eyJ...) returns 200")
    void meEndpointWithValidJwtPrefixShouldReturn200() {
        given()
            .header("Authorization", "Bearer " + VALID_JWT)
        .when()
            .get("/api/v1/auth/me")
        .then()
            .statusCode(200)
            .body("userId", notNullValue())
            .body("role", equalTo("CUSTOMER"));
    }

    @Test
    @DisplayName("GET /auth/me - Token without 'eyJ' prefix returns 401 (invalid format)")
    void meEndpointWithNonJwtTokenShouldReturn401() {
        given()
            .header("Authorization", "Bearer not-a-valid-jwt-token")
        .when()
            .get("/api/v1/auth/me")
        .then()
            .statusCode(401);
    }

    @Test
    @DisplayName("POST /auth/logout - Request with Bearer token returns 200")
    void logoutWithBearerTokenShouldReturn200() {
        given()
            .header("Authorization", "Bearer " + VALID_JWT)
        .when()
            .post("/api/v1/auth/logout")
        .then()
            .statusCode(200);
    }
}
