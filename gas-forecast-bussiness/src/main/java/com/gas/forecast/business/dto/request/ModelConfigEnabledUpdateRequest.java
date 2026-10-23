package com.gas.forecast.business.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 模型启用状态更新请求。 */
@Data
public class ModelConfigEnabledUpdateRequest {
    @NotNull(message = "ID不能为空")
    private Long id;

    @NotNull(message = "启用状态不能为空")
    @Min(value = 0, message = "启用状态只支持0或1")
    @Max(value = 1, message = "启用状态只支持0或1")
    private Integer enabled;
}
