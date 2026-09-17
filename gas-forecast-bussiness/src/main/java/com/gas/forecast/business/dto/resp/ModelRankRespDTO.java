package com.gas.forecast.business.dto.resp;

import java.math.BigDecimal;

public record ModelRankRespDTO(
        String modelName,
        String modelType,
        BigDecimal mape,
        BigDecimal wmape,
        BigDecimal rmse,
        BigDecimal mae,
        BigDecimal r2,
        String constituents
) {
}
