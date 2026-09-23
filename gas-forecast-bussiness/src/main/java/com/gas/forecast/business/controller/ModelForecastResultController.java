package com.gas.forecast.business.controller;

import com.gas.forecast.business.dto.request.ModelForecastBatchRequest;
import com.gas.forecast.business.dto.request.ModelForecastResultPageRequest;
import com.gas.forecast.business.dto.response.ModelForecastHistoryPointResponse;
import com.gas.forecast.business.service.ModelForecastManagementService;
import com.gas.forecast.common.core.PageInfoDTO;
import com.gas.forecast.common.core.ResponseResult;
import com.gas.forecast.common.web.WebLog;
import com.gas.forecast.dao.domain.ModelForecastResultTb;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模型预测结果接口。
 */
@RestController
@RequestMapping("/model-forecast-result")
@RequiredArgsConstructor
public class ModelForecastResultController {
    private final ModelForecastManagementService modelForecastManagementService;

    /**
     * 分页查询模型预测结果。
     *
     * @param reqDTO 模型预测结果分页查询条件
     * @return 模型预测结果分页数据
     */
    @PostMapping("listPage")
    @WebLog("模型预测结果分页查询")
    public ResponseResult<PageInfoDTO<ModelForecastResultTb>> listPage(
            @Valid @RequestBody ModelForecastResultPageRequest reqDTO) {
        return ResponseResult.success(modelForecastManagementService.listResults(reqDTO));
    }

    /**
     * 查询预测批次对应的历史对比数据。
     *
     * @param reqDTO 模型预测批次参数
     * @return 历史实际值与预测值对比数据
     */
    @PostMapping("history")
    @WebLog("模型预测历史数据查询")
    public ResponseResult<List<ModelForecastHistoryPointResponse>> history(
            @Valid @RequestBody ModelForecastBatchRequest reqDTO) {
        return ResponseResult.success(modelForecastManagementService.resultHistory(reqDTO));
    }
}
