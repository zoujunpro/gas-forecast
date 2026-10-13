package com.gas.forecast.business.controller;

import com.gas.forecast.business.dto.request.ModelTrainResultRequest;
import com.gas.forecast.business.dto.response.ModelTrainResultResponse;
import com.gas.forecast.business.dto.response.ModelTrainExecutionStepResponse;
import com.gas.forecast.business.service.ModelTrainExecutionService;
import com.gas.forecast.common.core.ResponseResult;
import com.gas.forecast.common.web.WebLog;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/**
 * 模型训练结果查询接口。
 */
@RestController
@RequestMapping({"/model-train-result", "/model-train-execution"})
@RequiredArgsConstructor
public class ModelTrainResultController {

    private final ModelTrainExecutionService modelTrainExecutionService;

    /**
     * 查询模型训练批次及其训练结果。
     *
     * @param reqDTO
     *            模型训练结果查询条件
     * @return 模型训练结果
     */
    @PostMapping({"query", "result"})
    @WebLog("查看模型训练结果")
    public ResponseResult<ModelTrainResultResponse> query(@Valid @RequestBody ModelTrainResultRequest reqDTO) {
        var result = modelTrainExecutionService.getTrainResult(reqDTO);
        return ResponseResult.success(result);
    }

    @PostMapping("steps")
    @WebLog("查看模型训练执行记录")
    public ResponseResult<List<ModelTrainExecutionStepResponse>> steps(@Valid @RequestBody ModelTrainResultRequest reqDTO) {
        return ResponseResult.success(modelTrainExecutionService.getExecutionSteps(reqDTO.getBatchNo()));
    }
}
