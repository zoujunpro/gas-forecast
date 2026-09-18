package com.gas.forecast.business.dto.resp;

/**
 * 模型特征关联应答参数。
 */
public record ModelFeatureRefRespDTO(
        Long id,
        Long featureId,
        String featureCode,
        String featureName,
        String featureColumn,
        String timeGranularity,
        Integer requiredFlag,
        Integer featureOrder
) {
}
