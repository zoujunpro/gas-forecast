package com.gas.forecast.business.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 区域新增请求参数。
 */
public record BaseRegionCreateReqDTO(
        /**
         * 区域名称。
         */
        @NotBlank(message = "区域名称不能为空")
        @Size(max = 128, message = "区域名称长度不能超过128个字符")
        String regionName,

        /**
         * 备注。
         */
        @Size(max = 255, message = "备注长度不能超过255个字符")
        String remark
) {
}
