package com.gas.forecast.business.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * 训练特征数据删除请求参数。
 */
public record ModelTrainFeatureDataDeleteReqDTO(
        @NotNull(message = "训练特征数据ID不能为空")
        Long id
) {
}
