package com.gas.forecast.business.controller;

import com.gas.forecast.business.dto.request.ModelForecastBatchRequest;
import com.gas.forecast.business.dto.response.ModelForecastAnalysisResponse;
import com.gas.forecast.business.service.ForecastAnalysisService;
import com.gas.forecast.common.core.ResponseResult;
import com.gas.forecast.common.web.WebLog;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/model-forecast-analysis")
@RequiredArgsConstructor
public class ModelForecastAnalysisController {
    private final ForecastAnalysisService forecastAnalysisService;

    @PostMapping("get")
    public ResponseResult<ModelForecastAnalysisResponse> get(@Valid @RequestBody ModelForecastBatchRequest request) {
        return ResponseResult.success(forecastAnalysisService.get(request.getForecastBatchNo()));
    }

    @PostMapping("retry")
    @WebLog("重新生成预测分析")
    public ResponseResult<Void> retry(@Valid @RequestBody ModelForecastBatchRequest request) {
        forecastAnalysisService.retry(request.getForecastBatchNo());
        return ResponseResult.success(null);
    }
}
