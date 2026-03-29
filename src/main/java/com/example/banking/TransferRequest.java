package com.example.banking;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request DTO for POST /api/v1/transfers
 */
public record TransferRequest(
    @JsonProperty("fromAccount") String fromAccount,
    @JsonProperty("toAccount") String toAccount,
    @JsonProperty("amount") double amount,
    @JsonProperty("currency") String currency,
    @JsonProperty("reference") String reference
) {}
