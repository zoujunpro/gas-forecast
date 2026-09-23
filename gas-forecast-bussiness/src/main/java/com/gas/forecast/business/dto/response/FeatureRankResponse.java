package com.gas.forecast.business.dto.response;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 特征重要性排名应答参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FeatureRankResponse {
    /**
     * 特征名称。
     */
    private String featureName;

    /**
     * 综合得分。
     */
    private BigDecimal score;

    /**
     * 树模型重要性。
     */
    private BigDecimal treeImportance;

    /**
     * 互信息得分。
     */
    private BigDecimal mutualInfo;
}
