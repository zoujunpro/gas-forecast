package com.gas.forecast.business.controller;

import com.gas.forecast.business.component.ModelPlatformClient;
import com.gas.forecast.business.component.dto.ModelInfoApiResponse;
import com.gas.forecast.common.core.ResponseResult;
import java.util.List;
import com.gas.forecast.common.web.WebLog;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Java 管理端访问模型平台的统一入口。 */
@RestController
@RequestMapping("/model-platform")
@RequiredArgsConstructor
public class ModelPlatformController {

    private final ModelPlatformClient modelPlatformClient;

    /**
     * 查询模型平台提供的模型列表。
     *
     * @return 模型平台模型列表
     */
    @GetMapping("/models")
    @WebLog("查询模型平台模型列表")
    public ResponseResult<List<ModelInfoApiResponse>> models() {
        var result = modelPlatformClient.listModels();
        return ResponseResult.success(result);
    }
}
