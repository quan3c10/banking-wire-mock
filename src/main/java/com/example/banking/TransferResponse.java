package com.example.banking;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for POST /api/v1/transfers
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TransferResponse(
    @JsonProperty("transferId") String transferId,
    @JsonProperty("status") String status,
    @JsonProperty("fromAccount") String fromAccount,
    @JsonProperty("toAccount") String toAccount,
    @JsonProperty("amount") double amount,
    @JsonProperty("currency") String currency
) {}
