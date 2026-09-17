package com.gas.forecast.business.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 区域更新请求参数。
 */
public record BaseRegionUpdateReqDTO(
        /**
         * 区域ID。
         */
        @NotNull(message = "区域ID不能为空")
        Long id,

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
