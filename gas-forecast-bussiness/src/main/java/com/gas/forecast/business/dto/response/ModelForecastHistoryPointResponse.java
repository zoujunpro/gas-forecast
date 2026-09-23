package com.gas.forecast.business.dto.response;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 模型预测历史对比点。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelForecastHistoryPointResponse {
    private String date;
    private BigDecimal actualValue;
    private BigDecimal predictedValue;
}
