package com.gas.forecast.common.util;

public final class ThreadLocalUtil {
    private static final ThreadLocal<LoginUser> LOGIN_USER = new ThreadLocal<>();

    private ThreadLocalUtil() {
    }

    public static void setLoginUser(Long userId, String username, String token) {
        LOGIN_USER.set(new LoginUser(userId, username, token));
    }

    public static LoginUser getLoginUser() {
        return LOGIN_USER.get();
    }

    public static Long getUserId() {
        LoginUser loginUser = getLoginUser();
        return loginUser == null ? null : loginUser.userId();
    }

    public static String getUsername() {
        LoginUser loginUser = getLoginUser();
        return loginUser == null ? null : loginUser.username();
    }

    public static String getToken() {
        LoginUser loginUser = getLoginUser();
        return loginUser == null ? null : loginUser.token();
    }

    public static void clear() {
        LOGIN_USER.remove();
    }

    public record LoginUser(Long userId, String username, String token) {
    }
}
