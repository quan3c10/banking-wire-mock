package com.example.wiremock;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@DisplayName("Customer Onboarding Flow")
class CustomerOnboardingTest extends BaseWireMockTest {

    @Test
    @DisplayName("POST /customers - Submit KYC returns 202 with applicationId")
    void submitKycShouldReturn202WithApplicationId() {
        Map<String, Object> kycPayload = Map.of(
            "name", "Jane Smith",
            "dateOfBirth", "1985-08-20",
            "idNumber", "ID-987654321",
            "address", Map.of(
                "street", "456 Oak Ave",
                "city", "Chicago",
                "state", "IL",
                "zipCode", "60601"
            )
        );

        given()
            .contentType(ContentType.JSON)
            .body(kycPayload)
        .when()
            .post("/api/v1/customers")
        .then()
            .statusCode(202)
            .contentType(ContentType.JSON)
            .body("applicationId", notNullValue())
            .body("status", equalTo("PENDING"))
            .body("message", containsString("submitted successfully"))
            .body("submittedAt", notNullValue());
    }

    @Test
    @DisplayName("GET /customers/applications/{id} - Check status echoes applicationId in response")
    void checkApplicationStatusEchoesApplicationId() {
        String applicationId = "app-abc-123";

        given()
        .when()
            .get("/api/v1/customers/applications/{applicationId}", applicationId)
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("applicationId", equalTo(applicationId))
            .body("status", notNullValue())
            .body("updatedAt", notNullValue());
    }

    @Test
    @DisplayName("GET /customers/applications/onb-app-001 - Default status is PENDING")
    void defaultApplicationStatusIsPending() {
        given()
        .when()
            .get("/api/v1/customers/applications/onb-app-001")
        .then()
            .statusCode(200)
            .body("status", equalTo("PENDING"));
    }

    @Test
    @DisplayName("POST /customers/applications/{id}/documents - Upload document returns 200 with documentId")
    void uploadDocumentShouldReturn200WithDocumentId() {
        String applicationId = "app-abc-123";
        Map<String, Object> documentPayload = Map.of(
            "documentType", "PASSPORT",
            "documentData", "base64encodedcontentgoeshere=="
        );

        given()
            .contentType(ContentType.JSON)
            .body(documentPayload)
        .when()
            .post("/api/v1/customers/applications/{applicationId}/documents", applicationId)
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("documentId", notNullValue())
            .body("applicationId", equalTo(applicationId))
            .body("status", equalTo("RECEIVED"))
            .body("uploadedAt", notNullValue());
    }

    @Test
    @DisplayName("GET /customers/{customerId} - Get customer profile returns full profile")
    void getCustomerProfileReturnsFullProfile() {
        String customerId = "cust-001";

        given()
        .when()
            .get("/api/v1/customers/{customerId}", customerId)
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("customerId", equalTo(customerId))
            .body("name", notNullValue())
            .body("email", notNullValue())
            .body("accounts", not(empty()))
            .body("accounts[0].accountId", notNullValue())
            .body("accounts[0].type", notNullValue())
            .body("status", equalTo("APPROVED"));
    }
}
