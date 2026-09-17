package com.gas.forecast.business.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 行业新增请求参数。
 */
public record BaseIndustryCreateReqDTO(
        /**
         * 行业名称。
         */
        @NotBlank(message = "行业名称不能为空")
        @Size(max = 128, message = "行业名称长度不能超过128个字符")
        String industryName
) {
}
