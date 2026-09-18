package com.gas.forecast.business.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * 模型训练配置删除请求参数。
 */
public record ModelTrainConfigDeleteReqDTO(
        @NotNull(message = "模型训练配置ID不能为空")
        Long id
) {
}
