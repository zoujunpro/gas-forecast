package com.gas.forecast.system.controller;

import com.gas.forecast.common.core.PageInfoDTO;
import com.gas.forecast.common.core.ResponseResult;
import com.gas.forecast.common.security.annotation.RequirePermission;
import com.gas.forecast.system.service.SystemManagementService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 系统用户管理接口。
 */
@RestController
@RequestMapping("/system/users")
public class SysUserController {

    private final SystemManagementService systemManagementService;

    public SysUserController(SystemManagementService systemManagementService) {
        this.systemManagementService = systemManagementService;
    }

    /**
     * 分页查询用户列表。
     */
    @PostMapping("listPage")
    @RequirePermission("sys:user:list")
    public ResponseResult<PageInfoDTO<Map<String, Object>>> listPage(@RequestBody Map<String, Object> req) {
        return ResponseResult.success(systemManagementService.listUsers(req));
    }

    /**
     * 新增或编辑用户。
     */
    @PostMapping("save")
    @RequirePermission("sys:user:save")
    public ResponseResult<Map<String, Object>> save(@RequestBody Map<String, Object> req) {
        return ResponseResult.success(systemManagementService.saveUser(req));
    }

    /**
     * 删除用户。
     */
    @GetMapping("delete")
    @RequirePermission("sys:user:delete")
    public ResponseResult<Void> delete(@RequestParam Long id) {
        systemManagementService.deleteUser(id);
        return ResponseResult.success(null);
    }
}
