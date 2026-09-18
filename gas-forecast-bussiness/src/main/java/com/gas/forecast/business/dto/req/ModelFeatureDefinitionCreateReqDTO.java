package com.gas.forecast.business.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 特征定义新增请求参数。
 */
public record ModelFeatureDefinitionCreateReqDTO(
        @NotBlank(message = "特征编号不能为空")
        @Size(max = 64, message = "特征编号长度不能超过64个字符")
        String featureCode,

        @NotBlank(message = "特征名称不能为空")
        @Size(max = 128, message = "特征名称长度不能超过128个字符")
        String featureName,

        @Size(max = 64, message = "宽表字段长度不能超过64个字符")
        String featureColumn,

        @NotBlank(message = "时间粒度不能为空")
        @Size(max = 16, message = "时间粒度长度不能超过16个字符")
        String timeGranularity,

        @NotNull(message = "启用状态不能为空")
        Integer enabled,

        @Size(max = 500, message = "描述长度不能超过500个字符")
        String description
) {
}
