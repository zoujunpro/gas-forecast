package com.gas.forecast.system.controller;

import com.gas.forecast.common.core.ResponseResult;
import com.gas.forecast.common.security.annotation.RequirePermission;
import com.gas.forecast.system.service.SystemManagementService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 系统菜单权限管理接口。
 */
@RestController
@RequestMapping("/system/permissions")
public class SysPermissionController {

    private final SystemManagementService systemManagementService;

    public SysPermissionController(SystemManagementService systemManagementService) {
        this.systemManagementService = systemManagementService;
    }

    /**
     * 查询菜单权限列表。
     */
    @GetMapping("list")
    @RequirePermission("sys:permission:list")
    public ResponseResult<List<Map<String, Object>>> list(@RequestParam(required = false) String keyword) {
        return ResponseResult.success(systemManagementService.listPermissions(keyword));
    }

    /**
     * 新增或编辑菜单权限。
     */
    @PostMapping("save")
    @RequirePermission("sys:permission:save")
    public ResponseResult<Map<String, Object>> save(@RequestBody Map<String, Object> req) {
        return ResponseResult.success(systemManagementService.savePermission(req));
    }

    /**
     * 删除菜单权限。
     */
    @GetMapping("delete")
    @RequirePermission("sys:permission:delete")
    public ResponseResult<Void> delete(@RequestParam Long id) {
        systemManagementService.deletePermission(id);
        return ResponseResult.success(null);
    }
}
