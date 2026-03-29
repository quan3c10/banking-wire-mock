package com.example.banking;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for POST /api/v1/auth/login
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LoginResponse(
    @JsonProperty("accessToken") String accessToken,
    @JsonProperty("refreshToken") String refreshToken,
    @JsonProperty("tokenType") String tokenType,
    @JsonProperty("expiresIn") int expiresIn,
    @JsonProperty("user") UserInfo user
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserInfo(
        @JsonProperty("userId") String userId,
        @JsonProperty("username") String username,
        @JsonProperty("role") String role
    ) {}
}
