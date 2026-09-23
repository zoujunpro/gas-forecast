package com.gas.forecast.business.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 模型预测提交结果。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelForecastExecuteResponse {
    private Long forecastId;
    private String forecastBatchNo;
    private Integer resultCount;
}
