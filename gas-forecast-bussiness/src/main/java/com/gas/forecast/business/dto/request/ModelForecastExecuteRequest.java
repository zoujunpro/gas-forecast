package com.gas.forecast.business.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import lombok.Data;

/** 执行模型预测请求。 */
@Data
public class ModelForecastExecuteRequest {
    @NotNull(message = "预测配置ID不能为空")
    private Long forecastId;

    /** 本次预测开始日期；不传时使用预测配置默认值。 */
    private String forecastStartDate;

    /** 本次预测步长；不传时使用预测配置默认值。 */
    private Integer forecastHorizon;

    /** 本次预测时间颗粒度；不传时使用预测配置默认值。 */
    private String forecastFrequency;

    private List<Map<String, Object>> dataset;

    /** 重新预测时传入的原预测批次号；首次预测不传。 */
    private String retryBatchNo;
}
