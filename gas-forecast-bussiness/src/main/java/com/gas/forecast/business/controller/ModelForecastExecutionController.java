package com.gas.forecast.business.controller;

import com.gas.forecast.business.dto.request.ModelForecastExecuteRequest;
import com.gas.forecast.business.dto.response.ModelForecastExecuteResponse;
import com.gas.forecast.business.service.ModelForecastManagementService;
import com.gas.forecast.common.core.ResponseResult;
import com.gas.forecast.common.web.WebLog;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模型预测执行接口。
 */
@RestController
@RequestMapping("/model-forecast-execution")
@RequiredArgsConstructor
public class ModelForecastExecutionController {
    private final ModelForecastManagementService modelForecastManagementService;

    /**
     * 执行模型预测。
     *
     * @param reqDTO 模型预测执行参数
     * @return 模型预测执行结果
     */
    @PostMapping("execute")
    @WebLog("执行模型预测")
    public ResponseResult<ModelForecastExecuteResponse> execute(
            @Valid @RequestBody ModelForecastExecuteRequest reqDTO) {
        return ResponseResult.success(modelForecastManagementService.execute(reqDTO));
    }
}
