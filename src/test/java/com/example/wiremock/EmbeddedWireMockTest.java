package com.example.wiremock;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Demonstrates using WireMock as an embedded JUnit 5 extension.
 * No Docker required — WireMock starts inside the test JVM on a random port.
 *
 * This approach is useful for unit-level testing of HTTP clients.
 */
@DisplayName("Embedded WireMock (no Docker required)")
class EmbeddedWireMockTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort())
        .build();

    @BeforeEach
    void configureRestAssured() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = wireMock.getPort();
    }

    @Test
    @DisplayName("Programmatic stub: POST /auth/login returns JWT")
    void loginStubDefinedProgrammatically() {
        wireMock.stubFor(
            post(urlEqualTo("/api/v1/auth/login"))
                .withRequestBody(matchingJsonPath("$.username", equalTo("embedded.user")))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {
                          "accessToken": "embedded-jwt-token",
                          "tokenType": "Bearer",
                          "expiresIn": 3600
                        }
                        """))
        );

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", "embedded.user", "password", "pass"))
        .when()
            .post("/api/v1/auth/login")
        .then()
            .statusCode(200)
            .body("accessToken", equalTo("embedded-jwt-token"))
            .body("tokenType", equalTo("Bearer"));
    }

    @Test
    @DisplayName("Programmatic stub: GET /accounts/{id}/balance returns balance")
    void balanceStubDefinedProgrammatically() {
        wireMock.stubFor(
            get(urlPathMatching("/api/v1/accounts/[A-Z0-9-]+/balance"))
                .withHeader("Authorization", matching("Bearer .+"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {
                          "balance": 5000.00,
                          "currency": "USD"
                        }
                        """))
        );

        given()
            .header("Authorization", "Bearer some-test-token")
        .when()
            .get("/api/v1/accounts/ACC-TEST/balance")
        .then()
            .statusCode(200)
            .body("balance", equalTo(5000.0f))
            .body("currency", equalTo("USD"));
    }

    @Test
    @DisplayName("Programmatic stub: Verify request was received")
    void verifyStubRequestWasReceived() {
        wireMock.stubFor(
            post(urlEqualTo("/api/v1/transfers"))
                .willReturn(aResponse()
                    .withStatus(202)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {
                          "transferId": "test-transfer-001",
                          "status": "INITIATED"
                        }
                        """))
        );

        given()
            .contentType(ContentType.JSON)
            .body(Map.of(
                "fromAccount", "ACC-001",
                "toAccount", "ACC-002",
                "amount", 100.00,
                "currency", "USD",
                "reference", "TEST-REF"
            ))
        .when()
            .post("/api/v1/transfers")
        .then()
            .statusCode(202)
            .body("transferId", equalTo("test-transfer-001"));

        // Verify the request was received exactly once
        wireMock.verify(1, postRequestedFor(urlEqualTo("/api/v1/transfers"))
            .withRequestBody(matchingJsonPath("$.fromAccount", equalTo("ACC-001"))));
    }

    @Test
    @DisplayName("Programmatic stub: POST /transfers with missing field returns 400")
    void incompleteTransferRequestShouldReturn400() {
        wireMock.stubFor(
            post(urlEqualTo("/api/v1/transfers"))
                .withRequestBody(matchingJsonPath("$.fromAccount"))
                .withRequestBody(matchingJsonPath("$.toAccount"))
                .withRequestBody(matchingJsonPath("$.amount"))
                .withRequestBody(matchingJsonPath("$.currency"))
                .withRequestBody(matchingJsonPath("$.reference"))
                .willReturn(aResponse().withStatus(202))
        );

        wireMock.stubFor(
            post(urlEqualTo("/api/v1/transfers"))
                .atPriority(5)
                .willReturn(aResponse()
                    .withStatus(400)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {"error": "VALIDATION_ERROR", "message": "Required fields missing"}
                        """))
        );

        // Send incomplete payload (missing 'reference')
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("fromAccount", "ACC-001", "toAccount", "ACC-002", "amount", 100.0))
        .when()
            .post("/api/v1/transfers")
        .then()
            .statusCode(400)
            .body("error", equalTo("VALIDATION_ERROR"));
    }
}
