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
    /**
     * ID。
     */
    private Long id;

    /**
     * 特征ID。
     */
    private Long featureId;

    /**
     * 特征编码。
     */
    private String featureCode;

    /**
     * 特征名称。
     */
    private String featureName;

    /**
     * 特征字段。
     */
    private String featureColumn;

    /**
     * 时间粒度。
     */
    private String timeGranularity;

    /**
     * 是否必填。
     */
    private Integer requiredFlag;

    /**
     * 特征排序。
     */
    private Integer featureOrder;
}
