package com.example.banking;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.*;

/**
 * Paradigm 2: WireMock as Automation Test Suite.
 *
 * The System Under Test (SUT) is BankingApiClient — real Java code
 * that makes HTTP calls. WireMock runs embedded in the JVM (no Docker needed).
 *
 * Each test:
 *  1. Stubs the endpoint (what WireMock should return)
 *  2. Calls the real client code (exercises the SUT)
 *  3. Asserts on the client's parsed return value
 *  4. Verifies WireMock received the correct request (URL, headers, body)
 *
 * This is the key difference from Paradigm 1:
 *  - Paradigm 1: REST Assured fires requests at WireMock, asserts on HTTP responses
 *  - Paradigm 2: Test drives the client, WireMock verifies the client behaves correctly
 */
@DisplayName("BankingApiClient - Paradigm 2 (WireMock as test suite)")
class BankingApiClientTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort())
        .build();

    private BankingApiClient client;

    @BeforeEach
    void setUp() {
        client = new BankingApiClient("http://localhost:" + wireMock.getPort());
    }

    // ─── Login ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("login: client sends correct JSON body and parses JWT response")
    void login_validCredentials_returnsTokenAndVerifiesRequest() {
        wireMock.stubFor(post(urlEqualTo("/api/v1/auth/login"))
            .willReturn(okJson("""
                {
                  "accessToken": "eyJhbGciOiJSUzI1NiJ9.test.signature",
                  "refreshToken": "rt_test_token",
                  "tokenType": "Bearer",
                  "expiresIn": 3600,
                  "user": {
                    "userId": "user-001",
                    "username": "john.doe",
                    "role": "CUSTOMER"
                  }
                }
                """)));

        LoginResponse response = client.login("john.doe", "SecureP@ss123");

        // Assert: client correctly parsed the response
        assertThat(response.accessToken()).isNotNull().startsWith("eyJ");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600);
        assertThat(response.user().username()).isEqualTo("john.doe");
        assertThat(response.user().role()).isEqualTo("CUSTOMER");

        // Verify: client sent the correct request (this is the unique value of Paradigm 2)
        wireMock.verify(postRequestedFor(urlEqualTo("/api/v1/auth/login"))
            .withHeader("Content-Type", containing("application/json"))
            .withRequestBody(matchingJsonPath("$.username", equalTo("john.doe")))
            .withRequestBody(matchingJsonPath("$.password", equalTo("SecureP@ss123"))));
    }

    @Test
    @DisplayName("login: 401 response causes BankingApiException with correct error code")
    void login_invalidCredentials_throwsBankingApiException() {
        wireMock.stubFor(post(urlEqualTo("/api/v1/auth/login"))
            .willReturn(aResponse()
                .withStatus(401)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"error": "INVALID_CREDENTIALS", "message": "Wrong username or password"}
                    """)));

        assertThatThrownBy(() -> client.login("wrong.user", "badpass"))
            .isInstanceOf(BankingApiException.class)
            .satisfies(ex -> {
                BankingApiException e = (BankingApiException) ex;
                assertThat(e.getStatusCode()).isEqualTo(401);
                assertThat(e.getErrorCode()).isEqualTo("INVALID_CREDENTIALS");
            });

        // Verify: client still sent a properly formed request even for bad credentials
        wireMock.verify(postRequestedFor(urlEqualTo("/api/v1/auth/login"))
            .withRequestBody(matchingJsonPath("$.username", equalTo("wrong.user"))));
    }

    @Test
    @DisplayName("login: 503 response causes BankingApiException(SERVICE_UNAVAILABLE)")
    void login_serviceUnavailable_throwsBankingApiException() {
        wireMock.stubFor(post(urlEqualTo("/api/v1/auth/login"))
            .willReturn(aResponse()
                .withStatus(503)
                .withHeader("Retry-After", "30")
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"error": "SERVICE_UNAVAILABLE", "retryAfter": 30}
                    """)));

        assertThatThrownBy(() -> client.login("any.user", "any"))
            .isInstanceOf(BankingApiException.class)
            .satisfies(ex -> {
                BankingApiException e = (BankingApiException) ex;
                assertThat(e.getStatusCode()).isEqualTo(503);
                assertThat(e.getErrorCode()).isEqualTo("SERVICE_UNAVAILABLE");
            });
    }

    // ─── Get Balance ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("getBalance: client sends Authorization header and parses balance")
    void getBalance_validToken_returnsBalanceAndVerifiesAuthHeader() {
        String token = "eyJhbGciOiJSUzI1NiJ9.test.sig";
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/accounts/ACC-001/balance"))
            .willReturn(okJson("""
                {
                  "accountId": "ACC-001",
                  "accountNumber": "1234567890",
                  "balance": 15000.00,
                  "availableBalance": 14500.00,
                  "currency": "USD"
                }
                """)));

        BalanceResponse response = client.getBalance("ACC-001", token);

        // Assert: client correctly parsed the balance response
        assertThat(response.accountId()).isEqualTo("ACC-001");
        assertThat(response.balance()).isEqualTo(15000.00);
        assertThat(response.availableBalance()).isEqualTo(14500.00);
        assertThat(response.currency()).isEqualTo("USD");

        // Verify: client sent the Authorization header with Bearer token
        wireMock.verify(getRequestedFor(urlPathEqualTo("/api/v1/accounts/ACC-001/balance"))
            .withHeader("Authorization", equalTo("Bearer " + token)));
    }

    @Test
    @DisplayName("getBalance: missing token causes 401 which becomes BankingApiException")
    void getBalance_missingToken_throwsBankingApiException() {
        wireMock.stubFor(get(urlPathMatching("/api/v1/accounts/.+/balance"))
            .willReturn(aResponse()
                .withStatus(401)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"error": "UNAUTHORIZED"}
                    """)));

        assertThatThrownBy(() -> client.getBalance("ACC-001", ""))
            .isInstanceOf(BankingApiException.class)
            .satisfies(ex -> assertThat(((BankingApiException) ex).getStatusCode()).isEqualTo(401));
    }

    // ─── Initiate Transfer ───────────────────────────────────────────────────

    @Test
    @DisplayName("initiateTransfer: client sends all required fields and parses transferId")
    void initiateTransfer_validRequest_returnsTransferAndVerifiesPayload() {
        String token = "eyJhbGciOiJSUzI1NiJ9.test.sig";
        wireMock.stubFor(post(urlEqualTo("/api/v1/transfers"))
            .willReturn(aResponse()
                .withStatus(202)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "transferId": "tf-abc-001",
                      "status": "INITIATED",
                      "fromAccount": "ACC-001",
                      "toAccount": "ACC-002",
                      "amount": 500.0,
                      "currency": "USD"
                    }
                    """)));

        TransferRequest request = new TransferRequest("ACC-001", "ACC-002", 500.00, "USD", "INV-001");
        TransferResponse response = client.initiateTransfer(request, token);

        // Assert: client correctly parsed the transfer response
        assertThat(response.transferId()).isEqualTo("tf-abc-001");
        assertThat(response.status()).isEqualTo("INITIATED");
        assertThat(response.fromAccount()).isEqualTo("ACC-001");
        assertThat(response.amount()).isEqualTo(500.00);

        // Verify: client sent all required fields in the request body AND the auth header
        wireMock.verify(postRequestedFor(urlEqualTo("/api/v1/transfers"))
            .withHeader("Content-Type", containing("application/json"))
            .withHeader("Authorization", equalTo("Bearer " + token))
            .withRequestBody(matchingJsonPath("$.fromAccount", equalTo("ACC-001")))
            .withRequestBody(matchingJsonPath("$.toAccount", equalTo("ACC-002")))
            .withRequestBody(matchingJsonPath("$[?(@.amount == 500.0)]"))
            .withRequestBody(matchingJsonPath("$.currency", equalTo("USD")))
            .withRequestBody(matchingJsonPath("$.reference", equalTo("INV-001"))));
    }

    @Test
    @DisplayName("initiateTransfer: 422 response causes BankingApiException(INSUFFICIENT_FUNDS)")
    void initiateTransfer_insufficientFunds_throwsBankingApiException() {
        wireMock.stubFor(post(urlEqualTo("/api/v1/transfers"))
            .willReturn(aResponse()
                .withStatus(422)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"error": "INSUFFICIENT_FUNDS", "availableBalance": 14500.0}
                    """)));

        TransferRequest request = new TransferRequest("ACC-001", "ACC-002", 99999.99, "USD", "OVERSPEND");

        assertThatThrownBy(() -> client.initiateTransfer(request, "any-token"))
            .isInstanceOf(BankingApiException.class)
            .satisfies(ex -> {
                BankingApiException e = (BankingApiException) ex;
                assertThat(e.getStatusCode()).isEqualTo(422);
                assertThat(e.getErrorCode()).isEqualTo("INSUFFICIENT_FUNDS");
            });

        // Verify: client still sent the correct payload before receiving the error
        wireMock.verify(postRequestedFor(urlEqualTo("/api/v1/transfers"))
            .withRequestBody(matchingJsonPath("$.fromAccount", equalTo("ACC-001")))
            .withRequestBody(matchingJsonPath("$[?(@.amount == 99999.99)]")));
    }

    @Test
    @DisplayName("initiateTransfer: 404 response causes BankingApiException(ACCOUNT_NOT_FOUND)")
    void initiateTransfer_invalidAccount_throwsBankingApiException() {
        wireMock.stubFor(post(urlEqualTo("/api/v1/transfers"))
            .willReturn(aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"error": "ACCOUNT_NOT_FOUND", "message": "Account does not exist"}
                    """)));

        TransferRequest request = new TransferRequest("ACC-INVALID", "ACC-002", 100.0, "USD", "TEST");

        assertThatThrownBy(() -> client.initiateTransfer(request, "any-token"))
            .isInstanceOf(BankingApiException.class)
            .satisfies(ex -> {
                BankingApiException e = (BankingApiException) ex;
                assertThat(e.getStatusCode()).isEqualTo(404);
                assertThat(e.getErrorCode()).isEqualTo("ACCOUNT_NOT_FOUND");
            });
    }

    @Test
    @DisplayName("initiateTransfer: connection failure causes BankingApiException(CONNECTION_ERROR)")
    void initiateTransfer_serverDown_throwsBankingApiException() {
        // Use WireMock fault injection to simulate a dropped connection
        wireMock.stubFor(post(urlEqualTo("/api/v1/transfers"))
            .willReturn(aResponse().withFault(
                com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)));

        TransferRequest request = new TransferRequest("ACC-001", "ACC-002", 100.0, "USD", "FAULT");

        assertThatThrownBy(() -> client.initiateTransfer(request, "any-token"))
            .isInstanceOf(BankingApiException.class)
            .satisfies(ex -> {
                BankingApiException e = (BankingApiException) ex;
                assertThat(e.getErrorCode()).isEqualTo("CONNECTION_ERROR");
                assertThat(e.getStatusCode()).isEqualTo(0);
            });
    }
}
