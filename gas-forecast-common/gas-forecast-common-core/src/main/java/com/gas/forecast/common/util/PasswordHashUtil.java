package com.gas.forecast.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class PasswordHashUtil {

    private PasswordHashUtil() {}

    public static String hash(String username, String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((username + ":" + password + ":" + salt).getBytes(StandardCharsets.UTF_8));
            return bytesToHexString(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to hash password", e);
        }
    }

    private static String bytesToHexString(byte[] src) {
        StringBuilder stringBuilder = new StringBuilder(src.length * 2);
        for (byte value : src) {
            String hex = Integer.toHexString(value & 0xFF);
            if (hex.length() < 2) {
                stringBuilder.append('0');
            }
            stringBuilder.append(hex);
        }
        return stringBuilder.toString();
    }
}
