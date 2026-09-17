package com.gas.forecast.business.dto.resp;

public record DimensionItemRespDTO(
        String code,
        String name,
        String parentCode,
        String type
) {
}
