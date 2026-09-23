package com.gas.forecast.business.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 预测结果点应答参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ForecastPointResponse {
    /**
     * 日期。
     */
    private LocalDate date;

    /**
     * 旬标签。
     */
    private String tendayLabel;

    /**
     * 平均温度。
     */
    private BigDecimal avgTemp;

    /**
     * 最高温度。
     */
    private BigDecimal maxTemp;

    /**
     * 最低温度。
     */
    private BigDecimal minTemp;

    /**
     * 采暖度日。
     */
    private BigDecimal hdd;

    /**
     * 极端低温天数。
     */
    private Integer extremeColdDays;

    /**
     * 气象数据来源。
     */
    private String weatherSource;

    /**
     * 预测值。
     */
    private BigDecimal prediction;

    /**
     * 预测下界。
     */
    private BigDecimal lower;

    /**
     * 预测上界。
     */
    private BigDecimal upper;
}
