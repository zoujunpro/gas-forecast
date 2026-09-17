package com.gas.forecast.business.dto.resp;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ForecastPointRespDTO(
        LocalDate date,
        String tendayLabel,
        BigDecimal avgTemp,
        BigDecimal maxTemp,
        BigDecimal minTemp,
        BigDecimal hdd,
        Integer extremeColdDays,
        String weatherSource,
        BigDecimal prediction,
        BigDecimal lower,
        BigDecimal upper
) {
}
