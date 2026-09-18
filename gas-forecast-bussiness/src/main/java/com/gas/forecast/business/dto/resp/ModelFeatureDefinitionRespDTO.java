package com.gas.forecast.business.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Date;

/**
 * 特征定义应答参数。
 */
public record ModelFeatureDefinitionRespDTO(
        Long id,
        String featureCode,
        String featureName,
        String featureColumn,
        String timeGranularity,
        Integer enabled,
        String description,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        Date createdAt,
        Long createdBy,
        String updatedByName
) {
}
