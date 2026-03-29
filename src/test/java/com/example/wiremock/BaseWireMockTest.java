package com.example.wiremock;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import static io.restassured.RestAssured.given;

/**
 * Base class for all WireMock integration tests.
 * Configures REST Assured to target the WireMock server and provides
 * shared helpers (scenario reset, authorized request builder).
 */
public abstract class BaseWireMockTest {

    protected static final String VALID_JWT =
            "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyLTAwMSIsIm5hbWUiOiJKb2huIERvZSIsInJvbGUiOiJDVVNUT01FUiIsImlhdCI6MTcwMDAwMDAwMCwiZXhwIjoxNzAwMDAzNjAwfQ.mock-signature";

    @BeforeAll
    static void configureRestAssured() {
        String host = System.getProperty("wiremock.host", "localhost");
        int port = Integer.parseInt(System.getProperty("wiremock.port", "8080"));
        RestAssured.baseURI = "http://" + host;
        RestAssured.port = port;
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @BeforeEach
    void resetScenarios() {
        // Reset all WireMock scenario states to "Started" before each test
        given()
            .contentType(ContentType.JSON)
            .post("/__admin/scenarios/reset")
            .then()
            .statusCode(200);
    }

    protected RequestSpecification authorizedRequest() {
        return given()
            .header("Authorization", "Bearer " + VALID_JWT)
            .contentType(ContentType.JSON);
    }

    protected Response adminGet(String path) {
        return given().get("/__admin" + path);
    }
}
