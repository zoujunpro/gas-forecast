package com.gas.forecast.business.controller;

import com.gas.forecast.business.dto.request.AgentStrategyConfigPageRequest;
import com.gas.forecast.business.dto.request.AgentStrategyConfigSaveRequest;
import com.gas.forecast.business.service.AgentStrategyConfigService;
import com.gas.forecast.common.core.ResponseResult;
import com.gas.forecast.common.security.annotation.RequirePermission;
import com.gas.forecast.common.web.WebLog;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/agent-strategy-config")
@RequiredArgsConstructor
public class AgentStrategyConfigController {
    private final AgentStrategyConfigService service;

    @PostMapping("listPage")
    @RequirePermission("agent:strategy:list")
    public ResponseResult<?> listPage(@Valid @RequestBody AgentStrategyConfigPageRequest request) {
        return ResponseResult.success(service.listPage(request));
    }

    @PostMapping("create")
    @WebLog("新增智能体策略")
    @RequirePermission("agent:strategy:create")
    public ResponseResult<?> create(@Valid @RequestBody AgentStrategyConfigSaveRequest request) {
        return ResponseResult.success(service.create(request));
    }

    @PostMapping("update")
    @WebLog("编辑智能体策略")
    @RequirePermission("agent:strategy:update")
    public ResponseResult<?> update(@Valid @RequestBody AgentStrategyConfigSaveRequest request) {
        return ResponseResult.success(service.update(request));
    }

    @GetMapping("delete")
    @WebLog("删除智能体策略")
    @RequirePermission("agent:strategy:delete")
    public ResponseResult<Void> delete(@RequestParam Long id) {
        service.delete(id);
        return ResponseResult.success(null);
    }
}
