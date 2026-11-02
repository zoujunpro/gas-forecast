package com.gas.forecast.business.controller;

import com.gas.forecast.business.dto.EntityBatchResolveRequest;
import com.gas.forecast.business.dto.IntentEntityResolveRequest;
import com.gas.forecast.business.dto.AgentRunEventCreateRequest;
import com.gas.forecast.business.dto.AgentRunEventResponse;
import com.gas.forecast.business.dto.AgentRunEventUpdateRequest;
import com.gas.forecast.business.service.AgentRunEventService;
import com.gas.forecast.business.service.BusinessEntityResolutionService;
import com.gas.forecast.common.core.ResponseResult;
import com.gas.forecast.common.security.annotation.RequirePermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final AgentRunEventService agentRunEventService;

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

    /** 创建真实运行事件并返回数据库 id，供 Python 后续更新同一条事件。 */
    @PostMapping("/runs/{runNo}/events")
    public ResponseResult<AgentRunEventResponse> createRunEvent(@PathVariable String runNo,
            @Valid @RequestBody AgentRunEventCreateRequest request) {
        return ResponseResult.success(agentRunEventService.create(runNo, request));
    }

    /** 更新同一运行事件；不得以追加新事件代替状态变化。 */
    @PatchMapping("/runs/{runNo}/events/{eventId}")
    public ResponseResult<AgentRunEventResponse> updateRunEvent(@PathVariable String runNo,
            @PathVariable Long eventId, @Valid @RequestBody AgentRunEventUpdateRequest request) {
        return ResponseResult.success(agentRunEventService.update(runNo, eventId, request));
    }
}
