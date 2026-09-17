package com.gas.forecast.business.dto.resp;

import java.util.List;

public record ForecastDimensionRespDTO(
        List<DimensionItemRespDTO> areas,
        List<DimensionItemRespDTO> provinces,
        List<DimensionItemRespDTO> customers
) {
}
