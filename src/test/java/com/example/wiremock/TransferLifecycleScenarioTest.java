package com.example.wiremock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * Tests WireMock stateful scenarios for the Fund Transfer lifecycle.
 *
 * The "Transfer Lifecycle" scenario transitions through:
 *   Started → INITIATED → PROCESSING → COMPLETED
 *
 * The "Transfer Failure" scenario transitions through:
 *   Started → INITIATED → FAILED
 *
 * Scenarios are reset via /__admin/scenarios/reset in BaseWireMockTest.@BeforeEach
 */
@DisplayName("Transfer Lifecycle Scenario")
class TransferLifecycleScenarioTest extends BaseWireMockTest {

    private static final String LIFECYCLE_TRANSFER = "/api/v1/transfers/lifecycle-tf-001";
    private static final String FAILED_TRANSFER = "/api/v1/transfers/failed-tf-001";

    @Test
    @DisplayName("Success path: INITIATED → PROCESSING → COMPLETED on sequential calls")
    void transferLifecycleShouldProgressThroughAllStates() {
        // Call 1: scenario state = Started → returns INITIATED, transitions to PROCESSING
        given()
        .when()
            .get(LIFECYCLE_TRANSFER)
        .then()
            .statusCode(200)
            .body("status", equalTo("INITIATED"))
            .body("transferId", equalTo("lifecycle-tf-001"));

        // Call 2: scenario state = PROCESSING → returns PROCESSING, transitions to COMPLETED
        given()
        .when()
            .get(LIFECYCLE_TRANSFER)
        .then()
            .statusCode(200)
            .body("status", equalTo("PROCESSING"))
            .body("transferId", equalTo("lifecycle-tf-001"));

        // Call 3: scenario state = COMPLETED → returns COMPLETED (terminal)
        given()
        .when()
            .get(LIFECYCLE_TRANSFER)
        .then()
            .statusCode(200)
            .body("status", equalTo("COMPLETED"))
            .body("transferId", equalTo("lifecycle-tf-001"));

        // Call 4: still COMPLETED (terminal state does not transition)
        given()
        .when()
            .get(LIFECYCLE_TRANSFER)
        .then()
            .statusCode(200)
            .body("status", equalTo("COMPLETED"));
    }

    @Test
    @DisplayName("Failure path: INITIATED → FAILED on sequential calls")
    void failedTransferLifecycleShouldProgressToFailed() {
        // Call 1: scenario state = Started → returns INITIATED, transitions to FAILED
        given()
        .when()
            .get(FAILED_TRANSFER)
        .then()
            .statusCode(200)
            .body("status", equalTo("INITIATED"))
            .body("transferId", equalTo("failed-tf-001"));

        // Call 2: scenario state = FAILED → returns FAILED (terminal)
        given()
        .when()
            .get(FAILED_TRANSFER)
        .then()
            .statusCode(200)
            .body("status", equalTo("FAILED"))
            .body("transferId", equalTo("failed-tf-001"))
            .body("errorCode", equalTo("PAYMENT_NETWORK_ERROR"));
    }

    @Test
    @DisplayName("Scenarios are independent: lifecycle and failure paths do not interfere")
    void lifecycleAndFailureScenariosShouldBeIndependent() {
        // Progress lifecycle-tf-001 to PROCESSING
        given().get(LIFECYCLE_TRANSFER).then().body("status", equalTo("INITIATED"));

        // failed-tf-001 should still be in Started state
        given().get(FAILED_TRANSFER).then().body("status", equalTo("INITIATED"));

        // lifecycle-tf-001 should now be in PROCESSING (next call after INITIATED)
        given().get(LIFECYCLE_TRANSFER).then().body("status", equalTo("PROCESSING"));

        // failed-tf-001 should now be in FAILED
        given().get(FAILED_TRANSFER).then().body("status", equalTo("FAILED"));
    }
}
