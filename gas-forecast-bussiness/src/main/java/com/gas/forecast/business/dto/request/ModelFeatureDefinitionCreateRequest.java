package com.gas.forecast.business.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 特征定义新增请求参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelFeatureDefinitionCreateRequest {
    @NotBlank(message = "特征编号不能为空")
    private @Size(max = 64, message = "特征编号长度不能超过64个字符") String featureCode;

    @NotBlank(message = "特征名称不能为空")
    private @Size(max = 128, message = "特征名称长度不能超过128个字符") String featureName;

    @NotBlank(message = "数据来源不能为空")
    private @Size(max = 20, message = "数据来源长度不能超过20个字符") String sourceType;

    @NotBlank(message = "取值来源不能为空")
    private @Size(max = 32, message = "取值来源长度不能超过32个字符") String valueSource;

    @Size(max = 64, message = "来源字段长度不能超过64个字符")
    private String sourceField;

    @Size(max = 32, message = "计算类型长度不能超过32个字符")
    private String calculationType;

    @Size(max = 1000, message = "计算参数长度不能超过1000个字符")
    private String calculationParams;

    @Size(max = 16, message = "聚合方式长度不能超过16个字符")
    private String aggregationMethod;

    @NotBlank(message = "缺失值策略不能为空")
    @Size(max = 16, message = "缺失值策略长度不能超过16个字符")
    private String missingValueStrategy;

    @NotBlank(message = "特征状态不能为空")
    private @Size(max = 20, message = "特征状态长度不能超过20个字符") String status;

    @NotNull(message = "启用状态不能为空")
    private Integer enabled;

    @Size(max = 500, message = "描述长度不能超过500个字符")
    private String description;

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
