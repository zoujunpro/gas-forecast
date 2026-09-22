package com.gas.forecast.business.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型特征关联应答参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelFeatureRefResponse {
    private Long id;

    private Long featureId;

    private String featureCode;

    private String featureName;

    private String featureColumn;

    private String timeGranularity;

    private Integer requiredFlag;

    private Integer featureOrder;

    public Long id() {
        return id;
    }

    public Long featureId() {
        return featureId;
    }

    public String featureCode() {
        return featureCode;
    }

    public String featureName() {
        return featureName;
    }

    public String featureColumn() {
        return featureColumn;
    }

    public String timeGranularity() {
        return timeGranularity;
    }

    public Integer requiredFlag() {
        return requiredFlag;
    }

    public Integer featureOrder() {
        return featureOrder;
    }
}
