package com.gas.forecast.common.security.token;

import com.gas.forecast.common.cache.CacheClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AuthTokenService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String TOKEN_KEY_PREFIX = "gas-forecast:auth:token:";

    private final CacheClient cacheClient;

    public AuthTokenService(CacheClient cacheClient) {
        this.cacheClient = cacheClient;
    }

    @Value("${gas.auth.secret:gas-forecast-local-secret}")
    private String secret;

    @Value("${gas.auth.token-expire-seconds:86400}")
    private long expireSeconds;

    public String createToken(Long userId, String username) {
        long expiresAt = Instant.now().getEpochSecond() + expireSeconds;
        String payload = userId + ":" + username + ":" + expiresAt;
        String token = base64Url(payload) + "." + sign(payload);
        cacheClient.put(tokenKey(token), username, Duration.ofSeconds(expireSeconds));
        return token;
    }

    public AuthTokenPayload parse(String token) {
        if (token == null || token.isBlank() || !token.contains(".")) {
            return null;
        }
        try {
            String[] parts = token.split("\\.", 2);
            String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            if (!sign(payload).equals(parts[1])) {
                return null;
            }
            String[] payloadParts = payload.split(":", 3);
            if (payloadParts.length != 3) {
                return null;
            }
            long expiresAt = Long.parseLong(payloadParts[2]);
            if (expiresAt < Instant.now().getEpochSecond()) {
                return null;
            }
            String username = payloadParts[1];
            String cachedUsername = cacheClient.get(tokenKey(token), String.class);
            if (!username.equals(cachedUsername)) {
                return null;
            }
            return new AuthTokenPayload(Long.parseLong(payloadParts[0]), username, expiresAt);
        } catch (Exception exception) {
            return null;
        }
    }

    public void deleteToken(String token) {
        if (token != null && !token.isBlank()) {
            cacheClient.evict(tokenKey(token));
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to sign auth token", exception);
        }
    }

    private String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String tokenKey(String token) {
        return TOKEN_KEY_PREFIX + token;
    }
}
