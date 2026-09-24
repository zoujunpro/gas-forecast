package com.gas.forecast.business.controller;

import com.gas.forecast.business.dto.request.ModelTrainFeatureDataPageRequest;
import com.gas.forecast.business.dto.response.ModelTrainFeatureDataResponse;
import com.gas.forecast.business.service.ModelTrainFeatureDataService;
import com.gas.forecast.common.core.PageInfoDTO;
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
 * 训练特征数据管理接口。
 */
@RestController
@RequestMapping("/model-train-feature-data")
@RequiredArgsConstructor
public class ModelTrainFeatureDataController {

    private final ModelTrainFeatureDataService modelTrainFeatureDataService;

    /**
     * 分页查询模型训练特征数据。
     *
     * @param reqDTO
     *            训练特征数据分页查询条件
     * @return 训练特征数据分页结果
     */
    @PostMapping("listPage")
    @WebLog("训练特征数据列表查询")
    @RequirePermission("model:train-feature-data:list")
    public ResponseResult<PageInfoDTO<ModelTrainFeatureDataResponse>> listPage(@Valid @RequestBody ModelTrainFeatureDataPageRequest reqDTO) {
        var result = modelTrainFeatureDataService.listPage(reqDTO);
        return ResponseResult.success(result);
    }
}
