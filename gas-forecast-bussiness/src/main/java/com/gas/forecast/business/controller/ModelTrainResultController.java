package com.gas.forecast.business.controller;

import com.gas.forecast.business.dto.request.ModelTrainResultRequest;
import com.gas.forecast.business.dto.response.ModelTrainResultResponse;
import com.gas.forecast.business.service.ModelTrainExecutionService;
import com.gas.forecast.common.core.ResponseResult;
import com.gas.forecast.common.web.WebLog;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模型训练结果查询接口。
 */
@RestController
@RequestMapping("/model-train-result")
@RequiredArgsConstructor
public class ModelTrainResultController {

    private final ModelTrainExecutionService modelTrainExecutionService;

    /**
     * 查询模型训练批次及其训练结果。
     *
     * @param reqDTO 模型训练结果查询条件
     * @return 模型训练结果
     */
    @PostMapping("query")
    @WebLog("查看模型训练结果")
    public ResponseResult<ModelTrainResultResponse> query(@Valid @RequestBody ModelTrainResultRequest reqDTO) {
        return ResponseResult.success(modelTrainExecutionService.getTrainResult(reqDTO));
    }
}
