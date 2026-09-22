package com.gas.forecast.business.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 区域删除请求参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BaseRegionDeleteRequest {
    /**
     * 区域ID。
     */
    @NotNull(message = "区域ID不能为空")
    private Long id;

    public Long id() {
        return id;
    }
}
