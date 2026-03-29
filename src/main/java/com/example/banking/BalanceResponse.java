package com.example.banking;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for GET /api/v1/accounts/{accountId}/balance
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BalanceResponse(
    @JsonProperty("accountId") String accountId,
    @JsonProperty("accountNumber") String accountNumber,
    @JsonProperty("balance") double balance,
    @JsonProperty("availableBalance") double availableBalance,
    @JsonProperty("currency") String currency
) {}
