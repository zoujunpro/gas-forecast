package com.gas.forecast.business.controller;

import com.gas.forecast.business.dto.request.ModelForecastExecuteRequest;
import com.gas.forecast.business.dto.request.ScopedForecastStartRequest;
import com.gas.forecast.business.dto.response.ModelForecastExecuteResponse;
import com.gas.forecast.business.service.ForecastApplicationService;
import com.gas.forecast.business.service.ModelForecastManagementService;
import com.gas.forecast.common.core.ResponseResult;
import com.gas.forecast.common.security.annotation.RequirePermission;
import com.gas.forecast.common.web.WebLog;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/**
 * 模型预测执行接口。
 */
@RestController
@RequestMapping("/model-forecast-execution")
@RequiredArgsConstructor
public class ModelForecastExecutionController {
    private final ModelForecastManagementService modelForecastManagementService;
    private final ForecastApplicationService forecastApplicationService;

    /** 页面按钮按当前已选范围直接预测；明确业务动作不经过AI会话。 */
    @PostMapping("start")
    @WebLog("按页面范围直接发起预测")
    @RequirePermission("model:forecast:execute")
    public ResponseResult<Object> start(@Valid @RequestBody ScopedForecastStartRequest request) {
        var command = new ForecastApplicationService.ForecastCommand(
                null, request.getAgentCode(), List.of(request.getCustomerCode()), request.getRegionCode(), request.getIndustryCode(),
                List.of(), List.of(), false,
                null, null, request.getForecastHorizon(), request.getForecastFrequency(), "ALL", null, null, null,
                false, Boolean.TRUE.equals(request.getAutoTrain()), request.getTrainingPreference(),
                Boolean.TRUE.equals(request.getConfirmed()), request.getIdempotencyKey());
        return ResponseResult.success(forecastApplicationService.start(command));
    }

    /** 页面直接预测的批次状态查询，不依赖AI会话ID。 */
    @PostMapping("status")
    @WebLog("查询直接预测状态")
    @RequirePermission("model:forecast:execute")
    public ResponseResult<Object> status(@RequestBody java.util.Map<String, String> request) {
        return ResponseResult.success(forecastApplicationService.status(request.get("forecastBatchNo")));
    }

    /**
     * 执行模型预测。
     *
     * @param reqDTO
     *            模型预测执行参数
     * @return 模型预测执行结果
     */
    @PostMapping("execute")
    @WebLog("执行模型预测")
    @RequirePermission("model:forecast:execute")
    public ResponseResult<ModelForecastExecuteResponse> execute(@Valid @RequestBody ModelForecastExecuteRequest reqDTO) {
        var result = modelForecastManagementService.execute(reqDTO);
        return ResponseResult.success(result);
    }
}
