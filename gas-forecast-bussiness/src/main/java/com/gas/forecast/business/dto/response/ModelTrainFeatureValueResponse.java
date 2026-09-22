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
    private String featureNo;

    private String featureCode;

    private Double featureValue;

    public String featureNo() {
        return featureNo;
    }

    public String featureCode() {
        return featureCode;
    }

    public Double featureValue() {
        return featureValue;
    }
}
