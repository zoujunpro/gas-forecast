package com.gas.forecast.business.controller;

import com.gas.forecast.business.dto.request.ModelTrainExecuteRequest;
import com.gas.forecast.business.component.dto.ModelTrainApiResponse;
import com.gas.forecast.business.component.dto.ModelTrainingValidationApiResponse;
import com.gas.forecast.business.dto.response.ModelTrainExecuteResponse;
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

    /**
     * 提交模型训练任务。
     *
     * @param reqDTO
     *            模型训练执行参数
     * @return 模型训练任务提交结果
     */
    @PostMapping("execute")
    @WebLog("执行模型训练")
    @RequirePermission("config:train:execute")
    public ResponseResult<ModelTrainExecuteResponse> execute(@Valid @RequestBody ModelTrainExecuteRequest reqDTO) {
        var result = modelTrainExecutionService.execute(reqDTO);
        return ResponseResult.success(result);
    }

    /**
     * 校验模型训练数据是否满足执行条件。
     *
     * @param reqDTO
     *            模型训练执行参数
     * @return 模型训练数据校验结果
     */
    @PostMapping("validate")
    @WebLog("校验模型训练数据")
    @RequirePermission("config:train:execute")
    public ResponseResult<ModelTrainingValidationApiResponse> validate(@Valid @RequestBody ModelTrainExecuteRequest reqDTO) {
        var result = modelTrainExecutionService.validateTrainingData(reqDTO);
        return ResponseResult.success(result);
    }

    /**
     * 接收模型平台返回的训练结果。
     *
     * @param reqDTO
     *            模型平台训练结果
     * @return 训练结果更新信息
     */
    @PostMapping("callback")
    @WebLog("模型训练结果回调")
    public ResponseResult<ModelTrainExecuteResponse> callback(@Valid @RequestBody ModelTrainApiResponse reqDTO) {
        var result = modelTrainExecutionService.updateTrainResult(reqDTO);
        return ResponseResult.success(result);
    }
}
