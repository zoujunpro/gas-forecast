package com.gas.forecast.business.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 特征定义更新请求参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelFeatureDefinitionUpdateRequest {
    @NotNull(message = "特征定义ID不能为空")
    private Long id;

    @NotBlank(message = "特征编号不能为空")
    private @Size(max = 64, message = "特征编号长度不能超过64个字符") String featureCode;

    @NotBlank(message = "特征名称不能为空")
    private @Size(max = 128, message = "特征名称长度不能超过128个字符") String featureName;

    @Size(max = 64, message = "宽表字段长度不能超过64个字符")
    private String featureColumn;

    @NotBlank(message = "时间粒度不能为空")
    private @Size(max = 16, message = "时间粒度长度不能超过16个字符") String timeGranularity;

    @NotNull(message = "启用状态不能为空")
    private Integer enabled;

    @Size(max = 500, message = "描述长度不能超过500个字符")
    private String description;

    public Long id() {
        return id;
    }

    public String featureCode() {
        return featureCode;
    }

    public String featureName() {
        return featureName;
    }

    public String featureColumn() {
        return featureColumn;
    }

    public String timeGranularity() {
        return timeGranularity;
    }

    public Integer enabled() {
        return enabled;
    }

    public String description() {
        return description;
    }
}
