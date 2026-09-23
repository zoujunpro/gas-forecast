package com.gas.forecast.business.dto.request;

import jakarta.validation.constraints.Min;
import lombok.Data;

/** 模型预测执行记录分页查询请求。 */
@Data
public class ModelForecastRecordPageRequest {
    @Min(1)
    private Integer page;

    @Min(1)
    private Integer size;

    private Long forecastId;
    private String forecastBatchNo;
    private Integer status;
}
