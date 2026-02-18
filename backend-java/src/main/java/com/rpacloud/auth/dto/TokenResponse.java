package com.rpacloud.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TokenResponse {
    private String accessToken;
    private String tokenType;

    public static TokenResponse bearer(String token) {
        return new TokenResponse(token, "bearer");
    }
}
