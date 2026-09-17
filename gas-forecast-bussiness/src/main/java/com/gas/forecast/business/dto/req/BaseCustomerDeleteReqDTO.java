package com.gas.forecast.business.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * 客户删除请求参数。
 */
public record BaseCustomerDeleteReqDTO(
        /**
         * 客户ID。
         */
        @NotNull(message = "客户ID不能为空")
        Long id
) {
}
