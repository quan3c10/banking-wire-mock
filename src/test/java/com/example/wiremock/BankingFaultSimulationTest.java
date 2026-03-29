package com.example.wiremock;

import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import org.apache.http.params.CoreConnectionPNames;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests banking-specific fault simulation scenarios.
 *
 * Faults tested:
 * 1. Core banking timeout (slow-account): 5s delay on balance endpoint
 * 2. Payment gateway failure (ACC-FAULT): MALFORMED_RESPONSE_CHUNK fault
 * 3. Auth service unavailable (trigger.outage): 503 with Retry-After header
 */
@Tag("fault")
@DisplayName("Banking Fault Simulation")
class BankingFaultSimulationTest extends BaseWireMockTest {

    @Test
    @DisplayName("GET /accounts/slow-account/balance - Responds after fixed delay (simulates core banking timeout)")
    void slowAccountBalanceShouldRespondAfterDelay() {
        long start = System.currentTimeMillis();

        given()
            .header("Authorization", "Bearer " + VALID_JWT)
        .when()
            .get("/api/v1/accounts/slow-account/balance")
        .then()
            .statusCode(200)
            .body("accountId", equalTo("slow-account"))
            .body("currency", equalTo("USD"));

        long elapsed = System.currentTimeMillis() - start;
        // Should take at least 4.5 seconds (WireMock adds 5s delay)
        assert elapsed >= 4500 : "Expected response to be delayed at least 4500ms, was " + elapsed + "ms";
    }

    @Test
    @DisplayName("POST /transfers with ACC-FAULT - Payment gateway fault drops connection")
    void transferFromFaultAccountShouldDropConnection() {
        // Configure a short connect timeout to get a fast failure
        RestAssuredConfig config = RestAssured.config()
            .httpClient(HttpClientConfig.httpClientConfig()
                .setParam(CoreConnectionPNames.SO_TIMEOUT, 3000));

        Map<String, Object> transferPayload = Map.of(
            "fromAccount", "ACC-FAULT",
            "toAccount", "ACC-002",
            "amount", 100.00,
            "currency", "USD",
            "reference", "FAULT-TEST"
        );

        // WireMock's MALFORMED_RESPONSE_CHUNK causes the connection to close abnormally
        // REST Assured will throw a RuntimeException wrapping a SocketException
        assertThrows(Exception.class, () ->
            given()
                .config(config)
                .contentType(ContentType.JSON)
                .body(transferPayload)
            .when()
                .post("/api/v1/transfers")
            .then()
                .extract().response()
        );
    }

    @Test
    @DisplayName("POST /auth/login with trigger.outage - Returns 503 with Retry-After header")
    void loginWithOutageTriggerShouldReturn503WithRetryAfterHeader() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", "trigger.outage", "password", "any"))
        .when()
            .post("/api/v1/auth/login")
        .then()
            .statusCode(503)
            .header("Retry-After", equalTo("30"))
            .contentType(ContentType.JSON)
            .body("error", equalTo("SERVICE_UNAVAILABLE"))
            .body("retryAfter", equalTo(30))
            .body("message", notNullValue());
    }
}
