package com.gas.forecast.business.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * 特征定义删除请求参数。
 */
public record ModelFeatureDefinitionDeleteReqDTO(
        @NotNull(message = "特征定义ID不能为空")
        Long id
) {
}
