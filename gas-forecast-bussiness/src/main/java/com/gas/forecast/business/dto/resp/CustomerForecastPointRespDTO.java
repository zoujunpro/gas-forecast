package com.gas.forecast.business.dto.resp;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CustomerForecastPointRespDTO(
        String customerCode,
        String customerName,
        String customerType,
        LocalDate date,
        String tendayLabel,
        BigDecimal prediction,
        BigDecimal lower,
        BigDecimal upper
) {
}
