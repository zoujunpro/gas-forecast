package com.gas.forecast.system.controller;

import com.gas.forecast.common.core.ResponseResult;
import com.gas.forecast.common.security.annotation.Logical;
import com.gas.forecast.common.security.annotation.RequirePermission;
import com.gas.forecast.system.service.SystemManagementService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统管理公共选项接口。
 */
@RestController
@RequestMapping("/system")
@RequiredArgsConstructor
public class SysOptionController {

    private final SystemManagementService systemManagementService;

    /**
     * 查询用户、角色、部门、菜单等公共选项。
     */
    @GetMapping("options")
    @RequirePermission(
            value = {"sys:user:list", "sys:role:list", "sys:department:list", "sys:permission:list"},
            logical = Logical.OR)
    public ResponseResult<Map<String, Object>> options() {
        return ResponseResult.success(systemManagementService.options());
    }
}
