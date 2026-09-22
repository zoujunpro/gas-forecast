package com.gas.forecast.common.security.token;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthTokenPayload {
    private Long userId;

    private String username;

    private long expiresAt;

    public Long userId() {
        return userId;
    }

    public String username() {
        return username;
    }

    public long expiresAt() {
        return expiresAt;
    }
}
