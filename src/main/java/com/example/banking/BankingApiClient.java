package com.example.banking;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * HTTP client for the Banking API.
 *
 * Demonstrates Paradigm 2: this is the System Under Test (SUT).
 * BankingApiClientTest uses embedded WireMock to verify that this client
 * sends the correct HTTP requests and handles responses properly.
 */
public class BankingApiClient {

    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper objectMapper;

    public BankingApiClient(String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        this.objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * POST /api/v1/auth/login
     *
     * @return LoginResponse with access token on success
     * @throws BankingApiException on 401 (invalid credentials) or 503 (service unavailable)
     */
    public LoginResponse login(String username, String password) {
        String body = toJson(new LoginPayload(username, password));
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/v1/auth/login"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = send(request);

        return switch (response.statusCode()) {
            case 200 -> parse(response.body(), LoginResponse.class);
            case 401 -> {
                String errorCode = extractField(response.body(), "error");
                throw new BankingApiException(401, errorCode, "Invalid credentials");
            }
            case 503 -> throw new BankingApiException(503, "SERVICE_UNAVAILABLE",
                "Authentication service is temporarily unavailable");
            default -> throw new BankingApiException(response.statusCode(), "UNEXPECTED_ERROR",
                "Unexpected status: " + response.statusCode());
        };
    }

    /**
     * GET /api/v1/accounts/{accountId}/balance
     *
     * @param token Bearer access token
     * @return BalanceResponse with current balance
     * @throws BankingApiException on 401
     */
    public BalanceResponse getBalance(String accountId, String token) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/v1/accounts/" + accountId + "/balance"))
            .header("Authorization", "Bearer " + token)
            .GET()
            .build();

        HttpResponse<String> response = send(request);

        return switch (response.statusCode()) {
            case 200 -> parse(response.body(), BalanceResponse.class);
            case 401 -> throw new BankingApiException(401, "UNAUTHORIZED",
                "Missing or invalid authentication token");
            default -> throw new BankingApiException(response.statusCode(), "UNEXPECTED_ERROR",
                "Unexpected status: " + response.statusCode());
        };
    }

    /**
     * POST /api/v1/transfers
     *
     * @param transferRequest transfer details
     * @param token           Bearer access token
     * @return TransferResponse with transferId and INITIATED status
     * @throws BankingApiException on 404 (account not found), 422 (insufficient funds)
     */
    public TransferResponse initiateTransfer(TransferRequest transferRequest, String token) {
        String body = toJson(transferRequest);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/v1/transfers"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + token)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = send(request);

        return switch (response.statusCode()) {
            case 202 -> parse(response.body(), TransferResponse.class);
            case 404 -> {
                String errorCode = extractField(response.body(), "error");
                throw new BankingApiException(404, errorCode, "Account not found");
            }
            case 422 -> {
                String errorCode = extractField(response.body(), "error");
                throw new BankingApiException(422, errorCode, "Transfer validation failed");
            }
            default -> throw new BankingApiException(response.statusCode(), "UNEXPECTED_ERROR",
                "Unexpected status: " + response.statusCode());
        };
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new BankingApiException(0, "CONNECTION_ERROR",
                "Failed to connect to banking API: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BankingApiException(0, "INTERRUPTED", "Request was interrupted");
        }
    }

    private <T> T parse(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new BankingApiException(0, "PARSE_ERROR",
                "Failed to parse response: " + e.getMessage());
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize request: " + e.getMessage(), e);
        }
    }

    private String extractField(String json, String field) {
        try {
            JsonNode node = objectMapper.readTree(json);
            JsonNode value = node.get(field);
            return value != null ? value.asText() : "UNKNOWN";
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    // Internal payload record — not exposed in the API
    private record LoginPayload(String username, String password) {}
}
