package com.gas.forecast.business.ai.controller;

import com.gas.forecast.business.ai.dto.AiChatResponse;
import com.gas.forecast.business.ai.dto.AiConversationResponse;
import com.gas.forecast.business.ai.dto.AiConversationRenameRequest;
import com.gas.forecast.business.ai.dto.AiContinueForecastRequest;
import com.gas.forecast.business.ai.dto.AiTrainingProgressResponse;
import com.gas.forecast.business.ai.service.AiConversationService;
import com.gas.forecast.business.service.AgentTaskProgressService;
import com.gas.forecast.common.core.ResponseResult;
import com.gas.forecast.common.security.annotation.RequirePermission;
import com.gas.forecast.common.web.WebLog;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai/conversations")
@RequiredArgsConstructor
public class AiConversationController {
    private final AiConversationService conversationService;
    private final AgentTaskProgressService taskProgressService;

    @GetMapping
    @RequirePermission("ai:assistant:use")
    public ResponseResult<List<AiConversationResponse>> list() {
        return ResponseResult.success(conversationService.list());
    }

    @GetMapping("/detail")
    @RequirePermission("ai:assistant:use")
    public ResponseResult<AiConversationResponse> detail(@RequestParam String conversationNo) {
        return ResponseResult.success(conversationService.detail(conversationNo));
    }

    @GetMapping("/forecast-detail")
    @RequirePermission("ai:assistant:use")
    public ResponseResult<AiConversationResponse> forecastDetail(@RequestParam String forecastBatchNo) {
        return ResponseResult.success(conversationService.forecastDetail(forecastBatchNo));
    }

    @PostMapping("/rename")
    @RequirePermission("ai:assistant:use")
    public ResponseResult<Void> rename(@Valid @RequestBody AiConversationRenameRequest request) {
        conversationService.rename(request.getConversationNo(), request.getTitle());
        return ResponseResult.success(null);
    }

    @GetMapping("/delete")
    @RequirePermission("ai:assistant:use")
    public ResponseResult<Void> delete(@RequestParam String conversationNo) {
        conversationService.delete(conversationNo);
        return ResponseResult.success(null);
    }

    @GetMapping("/training-progress")
    @RequirePermission("ai:assistant:use")
    public ResponseResult<AiTrainingProgressResponse> trainingProgress(@RequestParam String batchNo) {
        return ResponseResult.success(taskProgressService.trainingProgress(batchNo));
    }

    @GetMapping("/forecast-progress")
    @RequirePermission("ai:assistant:use")
    public ResponseResult<AiChatResponse> forecastProgress(@RequestParam String conversationNo, @RequestParam String batchNo) {
        return ResponseResult.success(taskProgressService.forecastProgress(conversationNo, batchNo));
    }

    @PostMapping("/continue-forecast")
    @WebLog("训练完成后自动继续预测")
    @RequirePermission("ai:assistant:use")
    public ResponseResult<AiChatResponse> continueForecast(@Valid @RequestBody AiContinueForecastRequest request) {
        return ResponseResult.success(taskProgressService.continueForecast(request.conversationNo(), request.trainBatchNo()));
    }
}
