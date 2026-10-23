package com.gas.forecast.business.ai.controller;

import com.gas.forecast.business.ai.dto.EntityBatchResolveRequest;
import com.gas.forecast.business.ai.dto.IntentEntityResolveRequest;
import com.gas.forecast.business.service.BusinessEntityResolutionService;
import com.gas.forecast.common.core.ResponseResult;
import com.gas.forecast.common.security.annotation.RequirePermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仅供 Python Agent 编排层调用的内部接口。
 *
 * <p>实体校验不属于 MCP 业务工具，也不供 Vue 调用；权限仍按当前用户的
 * {@code ai:assistant:use} 权限校验，确保 Agent 不能越权读取主数据。</p>
 */
@RestController
@RequestMapping("/internal/agent")
@RequiredArgsConstructor
@RequirePermission("ai:assistant:use")
public class AgentInternalController {
    private final BusinessEntityResolutionService entityResolutionService;

    @PostMapping("/entities:resolve")
    public ResponseResult<BusinessEntityResolutionService.BatchResolutionResult> resolveEntities(
            @Valid @RequestBody EntityBatchResolveRequest request) {
        return ResponseResult.success(entityResolutionService.resolveBatch(
                new BusinessEntityResolutionService.BatchEntityQuery(
                        request.customerNames(), request.regionNames(), request.industryNames(), request.limit())));
    }

    /** 新协议：按用户表达的每个目标组合消歧，不压平地区、行业和客户条件。 */
    @PostMapping("/intent-entities:resolve")
    public ResponseResult<BusinessEntityResolutionService.IntentResolutionResult> resolveIntentEntities(
            @Valid @RequestBody IntentEntityResolveRequest request) {
        return ResponseResult.success(entityResolutionService.resolveIntent(
                new BusinessEntityResolutionService.IntentEntityQuery(request.intent(), request.dimensionType(),
                        request.targets().stream().map(target -> new BusinessEntityResolutionService.EntityTargetQuery(
                                target.customerNames(), target.regionNames(), target.industryNames())).toList(), request.limit())));
    }
}
