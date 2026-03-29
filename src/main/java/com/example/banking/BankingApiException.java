package com.example.banking;

/**
 * Thrown when the banking API returns an unexpected error response.
 */
public class BankingApiException extends RuntimeException {

    private final int statusCode;
    private final String errorCode;

    public BankingApiException(int statusCode, String errorCode, String message) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
