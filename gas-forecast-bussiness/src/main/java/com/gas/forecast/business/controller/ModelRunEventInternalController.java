package com.gas.forecast.business.controller;

import com.gas.forecast.business.dto.AgentRunEventCreateRequest;
import com.gas.forecast.business.dto.AgentRunEventUpdateRequest;
import com.gas.forecast.business.service.AgentRunEventService;
import com.gas.forecast.common.core.ResponseResult;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 模型平台专用的过程事件回调入口；当前内网部署不要求用户登录态。 */
@RestController
@RequestMapping("/internal/model-run-events")
@RequiredArgsConstructor
public class ModelRunEventInternalController {
    private final AgentRunEventService eventService;

    @PostMapping("/runs/{runNo}/events")
    public ResponseResult<Map<String, Long>> create(@PathVariable String runNo,
            @Valid @RequestBody AgentRunEventCreateRequest request) {
        Long id = eventService.startInternal(runNo, request.getEventCode().trim(), request.getMessage().trim(),
                request.getEventDataJson(), request.getForecastNo(), request.getTaskDetailNo());
        return ResponseResult.success(Map.of("id", id));
    }

    @PatchMapping("/runs/{runNo}/events/{eventId}")
    public ResponseResult<Void> update(@PathVariable String runNo, @PathVariable Long eventId,
            @Valid @RequestBody AgentRunEventUpdateRequest request) {
        eventService.updateInternal(eventId, request.getStatus(), request.getMessage().trim(),
                request.getEventDataJson(), request.getErrorCode(), request.getErrorMessage());
        return ResponseResult.success(null);
    }
}
