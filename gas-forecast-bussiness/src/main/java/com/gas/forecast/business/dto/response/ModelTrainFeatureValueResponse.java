package com.gas.forecast.business.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 训练特征值应答参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelTrainFeatureValueResponse {
    /**
     * 特征序号。
     */
    private String featureNo;

    /**
     * 特征编码。
     */
    private String featureCode;

    /**
     * 特征值。
     */
    private Double featureValue;
}
