package com.gas.forecast.business.controller;

import com.gas.forecast.business.dto.req.ModelTrainExecuteReqDTO;
import com.gas.forecast.business.dto.resp.ModelTrainExecuteRespDTO;
import com.gas.forecast.business.service.ModelTrainExecutionService;
import com.gas.forecast.common.core.ResponseResult;
import com.gas.forecast.common.security.annotation.RequirePermission;
import com.gas.forecast.common.web.WebLog;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模型训练执行接口。
 */
@RestController
@RequestMapping("/model-train-execution")
public class ModelTrainExecutionController {

    private final ModelTrainExecutionService modelTrainExecutionService;

    public ModelTrainExecutionController(ModelTrainExecutionService modelTrainExecutionService) {
        this.modelTrainExecutionService = modelTrainExecutionService;
    }

    @PostMapping("execute")
    @WebLog("执行模型训练")
    @RequirePermission("config:train:execute")
    public ResponseResult<ModelTrainExecuteRespDTO> execute(@Valid @RequestBody ModelTrainExecuteReqDTO reqDTO) {
        return ResponseResult.success(modelTrainExecutionService.execute(reqDTO));
    }
}
