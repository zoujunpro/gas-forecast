package com.gas.forecast.business.controller;

import com.gas.forecast.business.dto.request.ModelForecastBatchRequest;
import com.gas.forecast.business.dto.request.ModelForecastRecordPageRequest;
import com.gas.forecast.business.service.ModelForecastManagementService;
import com.gas.forecast.common.core.PageInfoDTO;
import com.gas.forecast.common.core.ResponseResult;
import com.gas.forecast.common.web.WebLog;
import com.gas.forecast.dao.domain.ModelForecastRecordTb;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模型预测执行记录接口。
 */
@RestController
@RequestMapping("/model-forecast-record")
@RequiredArgsConstructor
public class ModelForecastRecordController {
    private final ModelForecastManagementService modelForecastManagementService;

    /**
     * 分页查询模型预测执行记录。
     *
     * @param reqDTO 模型预测记录分页查询条件
     * @return 模型预测执行记录分页数据
     */
    @PostMapping("listPage")
    @WebLog("模型预测执行记录查询")
    public ResponseResult<PageInfoDTO<ModelForecastRecordTb>> listPage(
            @Valid @RequestBody ModelForecastRecordPageRequest reqDTO) {
        return ResponseResult.success(modelForecastManagementService.listRecords(reqDTO));
    }

    /**
     * 查询预测批次使用的特征快照。
     *
     * @param reqDTO 模型预测批次参数
     * @return 预测批次特征快照
     */
    @PostMapping("features")
    @WebLog("模型预测特征快照查询")
    public ResponseResult<List<Map<String, Object>>> features(
            @Valid @RequestBody ModelForecastBatchRequest reqDTO) {
        return ResponseResult.success(modelForecastManagementService.recordFeatures(reqDTO));
    }
}
