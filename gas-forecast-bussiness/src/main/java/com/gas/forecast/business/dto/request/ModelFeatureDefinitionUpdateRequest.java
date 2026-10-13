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

    @NotBlank(message = "数据来源不能为空")
    private @Size(max = 20, message = "数据来源长度不能超过20个字符") String sourceType;

    @NotBlank(message = "取值来源不能为空")
    private @Size(max = 32, message = "取值来源长度不能超过32个字符") String valueSource;

    @Size(max = 64)
    private String sourceField;
    @Size(max = 32)
    private String calculationType;
    @Size(max = 1000)
    private String calculationParams;
    @Size(max = 16)
    private String aggregationMethod;
    @NotBlank(message = "缺失值策略不能为空")
    @Size(max = 16)
    private String missingValueStrategy;

    @NotBlank(message = "特征状态不能为空")
    private @Size(max = 20, message = "特征状态长度不能超过20个字符") String status;

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

    public String sourceType() {
        return sourceType;
    }

    public String valueSource() {
        return valueSource;
    }
    public String sourceField() {
        return sourceField;
    }
    public String calculationType() {
        return calculationType;
    }
    public String calculationParams() {
        return calculationParams;
    }
    public String aggregationMethod() {
        return aggregationMethod;
    }
    public String missingValueStrategy() {
        return missingValueStrategy;
    }

    public String status() {
        return status;
    }

    public Integer enabled() {
        return enabled;
    }

    public String description() {
        return description;
    }
}
