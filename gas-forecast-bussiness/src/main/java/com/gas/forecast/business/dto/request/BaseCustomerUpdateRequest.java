package com.gas.forecast.business.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 客户更新请求参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BaseCustomerUpdateRequest {
    /**
     * 客户ID。
     */
    @NotNull(message = "客户ID不能为空")
    private Long id;

    /**
     * 客户名称。
     */
    @NotBlank(message = "客户名称不能为空")
    @Size(max = 128, message = "客户名称长度不能超过128个字符")
    private String customerName;

    /**
     * 所属行业名称。
     */
    @NotBlank(message = "行业名称不能为空")
    @Size(max = 128, message = "行业名称长度不能超过128个字符")
    private String industryName;

    /**
     * 所属区域名称。
     */
    @NotBlank(message = "区域名称不能为空")
    @Size(max = 128, message = "区域名称长度不能超过128个字符")
    private String regionName;

    /**
     * 原始区域名称。
     */
    @Size(max = 128, message = "原始区域名称长度不能超过128个字符")
    private String rawRegionName;

    /**
     * 原始行业名称。
     */
    @Size(max = 128, message = "原始行业名称长度不能超过128个字符")
    private String rawIndustryName;

    public Long id() {
        return id;
    }

    public String customerName() {
        return customerName;
    }

    public String industryName() {
        return industryName;
    }

    public String regionName() {
        return regionName;
    }

    public String rawRegionName() {
        return rawRegionName;
    }

    public String rawIndustryName() {
        return rawIndustryName;
    }
}
