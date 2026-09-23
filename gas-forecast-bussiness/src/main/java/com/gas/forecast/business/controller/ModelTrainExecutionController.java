package com.gas.forecast.business.controller;

import com.gas.forecast.business.dto.request.ModelTrainExecuteRequest;
import com.gas.forecast.business.dto.request.ModelTrainResultRequest;
import com.gas.forecast.business.dto.response.ModelTrainAgentResponse;
import com.gas.forecast.business.dto.response.ModelTrainExecuteResponse;
import com.gas.forecast.business.dto.response.ModelTrainResultResponse;
import com.gas.forecast.business.dto.response.ModelTrainingValidationResponse;
import com.gas.forecast.business.service.ModelTrainExecutionService;
import com.gas.forecast.common.core.ResponseResult;
import com.gas.forecast.common.security.annotation.RequirePermission;
import com.gas.forecast.common.web.WebLog;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模型训练执行接口。
 */
@RestController
@RequestMapping("/model-train-execution")
@RequiredArgsConstructor
public class ModelTrainExecutionController {

    private final ModelTrainExecutionService modelTrainExecutionService;

    @PostMapping("execute")
    @WebLog("执行模型训练")
    @RequirePermission("config:train:execute")
    public ResponseResult<ModelTrainExecuteResponse> execute(@Valid @RequestBody ModelTrainExecuteRequest reqDTO) {
        return ResponseResult.success(modelTrainExecutionService.execute(reqDTO));
    }

    @PostMapping("validate")
    @WebLog("校验模型训练数据")
    @RequirePermission("config:train:execute")
    public ResponseResult<ModelTrainingValidationResponse> validate(
            @Valid @RequestBody ModelTrainExecuteRequest reqDTO) {
        return ResponseResult.success(modelTrainExecutionService.validateTrainingData(reqDTO));
    }

    @PostMapping("callback")
    @WebLog("模型训练结果回调")
    public ResponseResult<ModelTrainExecuteResponse> callback(
            @Valid @RequestBody ModelTrainAgentResponse reqDTO) {
        return ResponseResult.success(modelTrainExecutionService.updateTrainResult(reqDTO));
    }

    @PostMapping("result")
    @WebLog("查看模型训练结果")
    public ResponseResult<ModelTrainResultResponse> result(@Valid @RequestBody ModelTrainResultRequest reqDTO) {
        return ResponseResult.success(modelTrainExecutionService.getTrainResult(reqDTO));
    }
}
