package com.gas.forecast.business.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型回测明细应答参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BacktestDetailResponse {
    /**
     * 模型名称。
     */
    private String modelName;

    /**
     * 季节。
     */
    private String season;

    /**
     * 日期。
     */
    private LocalDate date;

    /**
     * 实际值。
     */
    private BigDecimal actual;

    /**
     * 预测值。
     */
    private BigDecimal prediction;

    /**
     * 绝对误差。
     */
    private BigDecimal absoluteError;

    /**
     * 绝对百分比误差。
     */
    private BigDecimal apePct;
}
