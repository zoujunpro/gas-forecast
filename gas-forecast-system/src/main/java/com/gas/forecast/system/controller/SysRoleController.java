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
 * 系统角色管理接口。
 */
@RestController
@RequestMapping("/system/roles")
@RequiredArgsConstructor
public class SysRoleController {

    private final SystemManagementService systemManagementService;

    /**
     * 分页查询角色列表。
     */
    @PostMapping("listPage")
    @RequirePermission("sys:role:list")
    public ResponseResult<PageInfoDTO<Map<String, Object>>> listPage(@RequestBody Map<String, Object> req) {
        var result = systemManagementService.listRoles(req);
        return ResponseResult.success(result);
    }

    /**
     * 新增或编辑角色。
     */
    @PostMapping("save")
    @RequirePermission("sys:role:save")
    public ResponseResult<Map<String, Object>> save(@RequestBody Map<String, Object> req) {
        var result = systemManagementService.saveRole(req);
        return ResponseResult.success(result);
    }

    /**
     * 删除角色。
     */
    @GetMapping("delete")
    @RequirePermission("sys:role:delete")
    public ResponseResult<Void> delete(@RequestParam Long id) {
        systemManagementService.deleteRole(id);
        return ResponseResult.success(null);
    }
}
