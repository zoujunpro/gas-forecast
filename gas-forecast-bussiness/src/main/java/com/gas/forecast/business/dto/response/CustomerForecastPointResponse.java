package com.gas.forecast.business.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 客户预测点应答参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerForecastPointResponse {
    /**
     * 客户编码。
     */
    private String customerCode;

    /**
     * 客户名称。
     */
    private String customerName;

    /**
     * 客户类型。
     */
    private String customerType;

    /**
     * 日期。
     */
    private LocalDate date;

    /**
     * 旬标签。
     */
    private String tendayLabel;

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
