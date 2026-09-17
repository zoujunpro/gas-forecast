package com.gas.forecast.business.dto.resp;

import java.math.BigDecimal;

public record ForecastSummaryRespDTO(
        String provinceCode,
        String province,
        String bestModel,
        BigDecimal backtestMape,
        BigDecimal backtestWmape,
        BigDecimal backtestRmse,
        Integer featureCount,
        Integer forecastHorizon,
        String weatherSource,
        String chartPath
) {
}
