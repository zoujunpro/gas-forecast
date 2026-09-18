package com.gas.forecast.business.dto.resp;

/**
 * 训练特征值应答参数。
 */
public record ModelTrainFeatureValueRespDTO(
        String featureNo,
        String featureCode,
        Double featureValue
) {
}
