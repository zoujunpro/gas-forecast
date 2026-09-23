package com.gas.forecast.business.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 模型预测提交结果。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelForecastExecuteResponse {
    /**
     * 预测任务ID。
     */
    private Long forecastId;
    /**
     * 预测批次号。
     */
    private String forecastBatchNo;
    /**
     * 预测结果数量。
     */
    private Integer resultCount;
}
