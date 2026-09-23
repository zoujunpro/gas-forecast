package com.gas.forecast.business.dto.request;

import jakarta.validation.constraints.Min;
import lombok.Data;

/** 模型预测结果分页查询请求。 */
@Data
public class ModelForecastResultPageRequest {
    @Min(1)
    private Integer page;
    @Min(1)
    private Integer size;
    private String forecastBatchNo;
}
