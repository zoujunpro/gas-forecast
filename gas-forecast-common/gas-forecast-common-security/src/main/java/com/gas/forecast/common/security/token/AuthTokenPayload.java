package com.gas.forecast.common.security.token;

public record AuthTokenPayload(Long userId, String username, long expiresAt) {
}
