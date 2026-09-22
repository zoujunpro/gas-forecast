package com.gas.forecast.business.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 客户删除请求参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BaseCustomerDeleteRequest {
    /**
     * 客户ID。
     */
    @NotNull(message = "客户ID不能为空")
    private Long id;

    public Long id() {
        return id;
    }
}
