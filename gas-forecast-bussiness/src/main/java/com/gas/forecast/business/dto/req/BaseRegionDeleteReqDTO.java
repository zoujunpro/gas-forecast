package com.gas.forecast.business.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * 区域删除请求参数。
 */
public record BaseRegionDeleteReqDTO(
        /**
         * 区域ID。
         */
        @NotNull(message = "区域ID不能为空")
        Long id
) {
}
