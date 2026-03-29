package com.example.wiremock;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@DisplayName("Fund Transfer Flow")
class FundTransferTest extends BaseWireMockTest {

    @Test
    @DisplayName("GET /accounts/{id}/balance - Returns balance with accountId echoed")
    void getBalanceShouldReturnAccountBalance() {
        given()
            .header("Authorization", "Bearer " + VALID_JWT)
        .when()
            .get("/api/v1/accounts/ACC-001/balance")
        .then()
            .statusCode(200)
            .body("accountId", equalTo("ACC-001"))
            .body("balance", notNullValue())
            .body("availableBalance", notNullValue())
            .body("currency", equalTo("USD"))
            .body("asOf", notNullValue());
    }

    @Test
    @DisplayName("GET /accounts/{id}/balance - Missing Authorization returns 401")
    void getBalanceWithoutAuthShouldReturn401() {
        given()
        .when()
            .get("/api/v1/accounts/ACC-001/balance")
        .then()
            .statusCode(401)
            .body("error", equalTo("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("POST /transfers - Valid transfer initiates successfully with 202")
    void initiateValidTransferShouldReturn202() {
        Map<String, Object> transferPayload = Map.of(
            "fromAccount", "ACC-001",
            "toAccount", "ACC-002",
            "amount", 500.00,
            "currency", "USD",
            "reference", "INVOICE-2025-001"
        );

        given()
            .contentType(ContentType.JSON)
            .body(transferPayload)
        .when()
            .post("/api/v1/transfers")
        .then()
            .statusCode(202)
            .body("transferId", notNullValue())
            .body("status", equalTo("INITIATED"))
            .body("fromAccount", equalTo("ACC-001"))
            .body("toAccount", equalTo("ACC-002"))
            .body("amount", equalTo(500.0f))
            .body("currency", equalTo("USD"))
            .body("reference", equalTo("INVOICE-2025-001"))
            .body("createdAt", notNullValue());
    }

    @Test
    @DisplayName("POST /transfers - Insufficient funds returns 422 with INSUFFICIENT_FUNDS error")
    void transferWithInsufficientFundsShouldReturn422() {
        Map<String, Object> transferPayload = Map.of(
            "fromAccount", "ACC-001",
            "toAccount", "ACC-002",
            "amount", 99999.99,
            "currency", "USD",
            "reference", "OVERSPEND-001"
        );

        given()
            .contentType(ContentType.JSON)
            .body(transferPayload)
        .when()
            .post("/api/v1/transfers")
        .then()
            .statusCode(422)
            .body("error", equalTo("INSUFFICIENT_FUNDS"))
            .body("availableBalance", notNullValue())
            .body("requestedAmount", equalTo(99999.99f));
    }

    @Test
    @DisplayName("POST /transfers - Invalid account returns 404 with ACCOUNT_NOT_FOUND error")
    void transferFromInvalidAccountShouldReturn404() {
        Map<String, Object> transferPayload = Map.of(
            "fromAccount", "ACC-INVALID",
            "toAccount", "ACC-002",
            "amount", 100.00,
            "currency", "USD",
            "reference", "TEST-INVALID"
        );

        given()
            .contentType(ContentType.JSON)
            .body(transferPayload)
        .when()
            .post("/api/v1/transfers")
        .then()
            .statusCode(404)
            .body("error", equalTo("ACCOUNT_NOT_FOUND"))
            .body("message", containsString("ACC-INVALID"));
    }

    @Test
    @DisplayName("GET /transfers/{id} - Get transfer status returns current status")
    void getTransferStatusShouldReturnStatus() {
        given()
        .when()
            .get("/api/v1/transfers/some-transfer-id")
        .then()
            .statusCode(200)
            .body("transferId", equalTo("some-transfer-id"))
            .body("status", notNullValue());
    }

    @Test
    @DisplayName("POST /transfers/{id}/cancel - Cancel pending transfer returns 200")
    void cancelPendingTransferShouldReturn200() {
        given()
            .contentType(ContentType.JSON)
        .when()
            .post("/api/v1/transfers/pending-transfer-001/cancel")
        .then()
            .statusCode(200)
            .body("transferId", equalTo("pending-transfer-001"))
            .body("status", equalTo("CANCELLED"))
            .body("cancelledAt", notNullValue());
    }

    @Test
    @DisplayName("POST /transfers/completed-transfer-001/cancel - Cannot cancel completed transfer (409)")
    void cancelCompletedTransferShouldReturn409() {
        given()
            .contentType(ContentType.JSON)
        .when()
            .post("/api/v1/transfers/completed-transfer-001/cancel")
        .then()
            .statusCode(409)
            .body("error", equalTo("TRANSFER_NOT_CANCELLABLE"))
            .body("currentStatus", equalTo("COMPLETED"));
    }
}
