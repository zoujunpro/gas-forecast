package com.gas.forecast.business.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 行业新增请求参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BaseIndustryCreateRequest {
    /**
     * 行业名称。
     */
    @NotBlank(message = "行业名称不能为空")
    @Size(max = 128, message = "行业名称长度不能超过128个字符")
    private String industryName;
}
