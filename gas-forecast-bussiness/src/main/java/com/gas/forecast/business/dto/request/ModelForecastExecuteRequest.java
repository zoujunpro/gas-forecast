package com.gas.forecast.business.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import lombok.Data;

/** 执行模型预测请求。 */
@Data
public class ModelForecastExecuteRequest {
    @NotNull(message = "预测配置ID不能为空")
    private Long forecastId;
    @NotEmpty(message = "请填写非空的特征数据数组")
    private List<Map<String, Object>> dataset;
}
