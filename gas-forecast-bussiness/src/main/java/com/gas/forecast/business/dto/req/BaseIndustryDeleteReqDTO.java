package com.gas.forecast.business.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * 行业删除请求参数。
 */
public record BaseIndustryDeleteReqDTO(
        /**
         * 行业ID。
         */
        @NotNull(message = "行业ID不能为空")
        Long id
) {
}
