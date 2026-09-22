package com.gas.forecast.business.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型配置删除请求参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelConfigDeleteRequest {
    @NotNull(message = "ID不能为空")
    private Long id;

    public Long id() {
        return id;
    }
}
