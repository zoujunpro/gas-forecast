package com.gas.forecast.business.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.gas.forecast.business.service.ModelPlatformService;
import com.gas.forecast.common.core.ResponseResult;
import com.gas.forecast.common.web.WebLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Java 管理端访问模型平台的统一入口。 */
@RestController
@RequestMapping("/model-platform")
public class ModelPlatformController {

    private final ModelPlatformService modelPlatformService;

    public ModelPlatformController(ModelPlatformService modelPlatformService) {
        this.modelPlatformService = modelPlatformService;
    }

    @GetMapping("/models")
    @WebLog("查询模型平台模型列表")
    public ResponseResult<JsonNode> models() {
        return ResponseResult.success(modelPlatformService.listModels());
    }
}
