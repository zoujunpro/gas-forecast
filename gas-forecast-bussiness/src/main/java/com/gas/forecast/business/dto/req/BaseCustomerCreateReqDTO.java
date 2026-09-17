package com.gas.forecast.business.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 客户新增请求参数。
 */
public record BaseCustomerCreateReqDTO(
        /**
         * 客户名称。
         */
        @NotBlank(message = "客户名称不能为空")
        @Size(max = 128, message = "客户名称长度不能超过128个字符")
        String customerName,

        /**
         * 所属行业名称。
         */
        @NotBlank(message = "行业名称不能为空")
        @Size(max = 128, message = "行业名称长度不能超过128个字符")
        String industryName,

        /**
         * 所属区域名称。
         */
        @NotBlank(message = "区域名称不能为空")
        @Size(max = 128, message = "区域名称长度不能超过128个字符")
        String regionName,

        /**
         * 原始区域名称。
         */
        @Size(max = 128, message = "原始区域名称长度不能超过128个字符")
        String rawRegionName,

        /**
         * 原始行业名称。
         */
        @Size(max = 128, message = "原始行业名称长度不能超过128个字符")
        String rawIndustryName
) {
}
