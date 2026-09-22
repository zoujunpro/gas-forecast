package com.gas.forecast.business.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 区域新增请求参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BaseRegionCreateRequest {
    /**
     * 区域名称。
     */
    @NotBlank(message = "区域名称不能为空")
    @Size(max = 128, message = "区域名称长度不能超过128个字符")
    private String regionName;

    /**
     * 备注。
     */
    @Size(max = 255, message = "备注长度不能超过255个字符")
    private String remark;

    public String regionName() {
        return regionName;
    }

    public String remark() {
        return remark;
    }
}
