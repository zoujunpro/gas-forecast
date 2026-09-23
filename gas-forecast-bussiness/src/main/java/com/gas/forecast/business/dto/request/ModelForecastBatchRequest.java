package com.gas.forecast.business.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 模型预测批次查询请求。 */
@Data
public class ModelForecastBatchRequest {
    @NotBlank(message = "预测批次号不能为空")
    private String forecastBatchNo;
}
