package com.gas.forecast.system.controller;

import com.gas.forecast.common.core.PageInfoDTO;
import com.gas.forecast.common.core.ResponseResult;
import com.gas.forecast.common.security.annotation.RequirePermission;
import com.gas.forecast.system.service.SystemManagementService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统用户管理接口。
 */
@RestController
@RequestMapping("/system/users")
@RequiredArgsConstructor
public class SysUserController {

    private final SystemManagementService systemManagementService;

    /**
     * 分页查询用户列表。
     */
    @PostMapping("listPage")
    @RequirePermission("sys:user:list")
    public ResponseResult<PageInfoDTO<Map<String, Object>>> listPage(@RequestBody Map<String, Object> req) {
        var result = systemManagementService.listUsers(req);
        return ResponseResult.success(result);
    }

    /**
     * 新增或编辑用户。
     */
    @PostMapping("save")
    @RequirePermission("sys:user:save")
    public ResponseResult<Map<String, Object>> save(@RequestBody Map<String, Object> req) {
        var result = systemManagementService.saveUser(req);
        return ResponseResult.success(result);
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
