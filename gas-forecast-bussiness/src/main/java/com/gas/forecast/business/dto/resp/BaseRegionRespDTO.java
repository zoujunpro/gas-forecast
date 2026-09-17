package com.gas.forecast.business.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Date;

public record BaseRegionRespDTO(
        Long id,
        String regionCode,
        String regionName,
        String remark,
        @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
        Date createdAt,
        @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
        Date updatedAt
) {
}
