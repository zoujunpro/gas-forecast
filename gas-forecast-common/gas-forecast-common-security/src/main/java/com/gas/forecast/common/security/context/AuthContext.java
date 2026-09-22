package com.gas.forecast.common.security.context;

import com.gas.forecast.common.security.token.AuthTokenPayload;

public final class AuthContext {
    private static final ThreadLocal<AuthTokenPayload> CURRENT = new ThreadLocal<>();

    private AuthContext() {}

    public static void set(AuthTokenPayload payload) {
        CURRENT.set(payload);
    }

    public static AuthTokenPayload get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
