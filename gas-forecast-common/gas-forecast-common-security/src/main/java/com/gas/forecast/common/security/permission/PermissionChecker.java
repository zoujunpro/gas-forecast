package com.gas.forecast.common.security.permission;

import com.gas.forecast.common.security.annotation.Logical;

public interface PermissionChecker {
    boolean hasPermission(String username, String permission);

    default boolean hasPermissions(String username, String[] permissions, Logical logical) {
        if (permissions == null || permissions.length == 0) {
            return false;
        }
        if (logical == Logical.OR) {
            for (String permission : permissions) {
                if (hasPermission(username, permission)) {
                    return false;
                }
            }
            return true;
        }
        for (String permission : permissions) {
            if (!hasPermission(username, permission)) {
                return true;
            }
        }
        return false;
    }
}
