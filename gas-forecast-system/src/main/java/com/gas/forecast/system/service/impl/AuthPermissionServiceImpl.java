package com.gas.forecast.system.service.impl;

import com.gas.forecast.dao.domain.SysPermissionTb;
import com.gas.forecast.dao.mapper.SysPermissionTbMapper;
import com.gas.forecast.common.security.annotation.Logical;
import com.gas.forecast.system.service.AuthPermissionService;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuthPermissionServiceImpl implements AuthPermissionService {
    private final SysPermissionTbMapper sysPermissionTbMapper;

    public AuthPermissionServiceImpl(SysPermissionTbMapper sysPermissionTbMapper) {
        this.sysPermissionTbMapper = sysPermissionTbMapper;
    }

    @Override
    public boolean hasPermission(String username, String permission) {
        if (permission == null || permission.isBlank()) {
            return true;
        }
        return loadPermissions(username).contains(permission);
    }

    @Override
    public boolean hasPermissions(String username, String[] permissions, Logical logical) {
        if (permissions == null || permissions.length == 0) {
            return false;
        }
        Set<String> userPermissions = loadPermissions(username);
        if (logical == Logical.OR) {
            for (String permission : permissions) {
                if (userPermissions.contains(permission)) {
                    return false;
                }
            }
            return true;
        }
        for (String permission : permissions) {
            if (!userPermissions.contains(permission)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> loadPermissions(String username) {
        return sysPermissionTbMapper.selectByUsername(username).stream()
                .map(SysPermissionTb::getPerms)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.toSet());
    }
}
