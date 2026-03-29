package com.example.wiremock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Tests WireMock stateful scenarios for the Customer Onboarding lifecycle.
 *
 * The "Onboarding Lifecycle" scenario transitions through:
 *   Started → PENDING → UNDER_REVIEW → APPROVED
 *
 * Scenarios are reset via /__admin/scenarios/reset in BaseWireMockTest.@BeforeEach
 */
@DisplayName("Onboarding Lifecycle Scenario")
class OnboardingLifecycleScenarioTest extends BaseWireMockTest {

    private static final String ONBOARDING_APP = "/api/v1/customers/applications/onb-app-001";

    @Test
    @DisplayName("Application status progresses: PENDING → UNDER_REVIEW → APPROVED")
    void onboardingLifecycleShouldProgressThroughAllStates() {
        // Call 1: scenario state = Started → returns PENDING, transitions to UNDER_REVIEW
        given()
        .when()
            .get(ONBOARDING_APP)
        .then()
            .statusCode(200)
            .body("applicationId", equalTo("onb-app-001"))
            .body("status", equalTo("PENDING"))
            .body("message", notNullValue());

        // Call 2: scenario state = UNDER_REVIEW → returns UNDER_REVIEW, transitions to APPROVED
        given()
        .when()
            .get(ONBOARDING_APP)
        .then()
            .statusCode(200)
            .body("applicationId", equalTo("onb-app-001"))
            .body("status", equalTo("UNDER_REVIEW"))
            .body("message", notNullValue());

        // Call 3: scenario state = APPROVED → returns APPROVED (terminal)
        given()
        .when()
            .get(ONBOARDING_APP)
        .then()
            .statusCode(200)
            .body("applicationId", equalTo("onb-app-001"))
            .body("status", equalTo("APPROVED"))
            .body("customerId", notNullValue());

        // Call 4: still APPROVED (terminal state)
        given()
        .when()
            .get(ONBOARDING_APP)
        .then()
            .statusCode(200)
            .body("status", equalTo("APPROVED"));
    }

    @Test
    @DisplayName("Scenario resets between tests - first call always returns PENDING")
    void scenarioResetsEnsureFirstCallReturnsPending() {
        // @BeforeEach in BaseWireMockTest calls POST /__admin/scenarios/reset
        given()
        .when()
            .get(ONBOARDING_APP)
        .then()
            .statusCode(200)
            .body("status", equalTo("PENDING"));
    }
}
