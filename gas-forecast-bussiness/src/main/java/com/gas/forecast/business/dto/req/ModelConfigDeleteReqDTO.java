package com.gas.forecast.business.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * 模型配置删除请求参数。
 */
public record ModelConfigDeleteReqDTO(
        @NotNull(message = "ID不能为空")
        Long id
) {
}
