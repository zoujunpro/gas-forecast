package com.gas.forecast.system.controller;

import com.gas.forecast.common.core.ResponseResult;
import com.gas.forecast.common.security.annotation.RequirePermission;
import com.gas.forecast.system.service.SystemManagementService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统部门管理接口。
 */
@RestController
@RequestMapping("/system/departments")
public class SysDepartmentController {

    private final SystemManagementService systemManagementService;

    public SysDepartmentController(SystemManagementService systemManagementService) {
        this.systemManagementService = systemManagementService;
    }

    /**
     * 查询部门列表。
     */
    @GetMapping("list")
    @RequirePermission("sys:department:list")
    public ResponseResult<List<Map<String, Object>>> list(@RequestParam(required = false) String keyword) {
        return ResponseResult.success(systemManagementService.listDepartments(keyword));
    }

    /**
     * 新增或编辑部门。
     */
    @PostMapping("save")
    @RequirePermission("sys:department:save")
    public ResponseResult<Map<String, Object>> save(@RequestBody Map<String, Object> req) {
        return ResponseResult.success(systemManagementService.saveDepartment(req));
    }

    /**
     * 删除部门。
     */
    @GetMapping("delete")
    @RequirePermission("sys:department:delete")
    public ResponseResult<Void> delete(@RequestParam Long id) {
        systemManagementService.deleteDepartment(id);
        return ResponseResult.success(null);
    }
}
