package com.gas.forecast.business.dto.resp;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BacktestDetailRespDTO(
        String modelName,
        String season,
        LocalDate date,
        BigDecimal actual,
        BigDecimal prediction,
        BigDecimal absoluteError,
        BigDecimal apePct
) {
}
