package com.gas.forecast.business.dto.response;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FeatureRankResponse {
    private String featureName;

    private BigDecimal score;

    private BigDecimal treeImportance;

    private BigDecimal mutualInfo;

    public String featureName() {
        return featureName;
    }

    public BigDecimal score() {
        return score;
    }

    public BigDecimal treeImportance() {
        return treeImportance;
    }

    public BigDecimal mutualInfo() {
        return mutualInfo;
    }
}
