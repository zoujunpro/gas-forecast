package com.gas.forecast.business.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 行业删除请求参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BaseIndustryDeleteRequest {
    /**
     * 行业ID。
     */
    @NotNull(message = "行业ID不能为空")
    private Long id;

    public Long id() {
        return id;
    }
}
