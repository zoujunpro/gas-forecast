package com.gas.forecast.business.controller;

import com.gas.forecast.business.dto.request.ModelTrainFeatureDataPageRequest;
import com.gas.forecast.business.dto.response.ModelTrainFeatureDataResponse;
import com.gas.forecast.business.service.ModelTrainFeatureDataService;
import com.gas.forecast.common.core.PageInfoDTO;
import com.gas.forecast.common.core.ResponseResult;
import com.gas.forecast.common.security.annotation.RequirePermission;
import com.gas.forecast.common.web.WebLog;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 训练特征数据管理接口。
 */
@RestController
@RequestMapping("/model-train-feature-data")
public class ModelTrainFeatureDataController {

    private final ModelTrainFeatureDataService modelTrainFeatureDataService;

    public ModelTrainFeatureDataController(ModelTrainFeatureDataService modelTrainFeatureDataService) {
        this.modelTrainFeatureDataService = modelTrainFeatureDataService;
    }

    @PostMapping("listPage")
    @WebLog("训练特征数据列表查询")
    @RequirePermission("model:train-feature-data:list")
    public ResponseResult<PageInfoDTO<ModelTrainFeatureDataResponse>> listPage(
            @Valid @RequestBody ModelTrainFeatureDataPageRequest reqDTO) {
        return ResponseResult.success(modelTrainFeatureDataService.listPage(reqDTO));
    }
}
