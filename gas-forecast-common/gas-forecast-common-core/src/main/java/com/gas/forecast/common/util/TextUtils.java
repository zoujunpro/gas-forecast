package com.gas.forecast.common.util;

import java.util.Collection;
import java.util.Map;

public final class TextUtils {

    private TextUtils() {}

    public static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public static boolean isEmpty(Collection<?> value) {
        return value == null || value.isEmpty();
    }

    public static boolean isEmpty(Map<?, ?> value) {
        return value == null || value.isEmpty();
    }

    public static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
