package com.gas.forecast.business.dto.resp;

import java.math.BigDecimal;

public record FeatureRankRespDTO(
        String featureName,
        BigDecimal score,
        BigDecimal treeImportance,
        BigDecimal mutualInfo
) {
}
