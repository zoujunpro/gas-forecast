package com.gas.forecast.common.security.permission;

import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.security.annotation.Logical;
import java.util.Arrays;

public final class PermissionUtil {
    private PermissionUtil() {
    }

    public static void checkPermissions(PermissionChecker permissionChecker, String username, String[] permissions, Logical logical) {
        if (permissionChecker.hasPermissions(username, permissions, logical)) {
            throw new BusinessException("403", "无权访问该功能：" + String.join(",", Arrays.asList(permissions)));
        }
    }
}
