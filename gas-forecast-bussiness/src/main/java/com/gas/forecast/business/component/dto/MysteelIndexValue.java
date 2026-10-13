package com.gas.forecast.business.component.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 钢联指标在指定日期的数值。 */
public record MysteelIndexValue(String indexCode, String indexName, LocalDate dataDate, BigDecimal dataValue) {
}
