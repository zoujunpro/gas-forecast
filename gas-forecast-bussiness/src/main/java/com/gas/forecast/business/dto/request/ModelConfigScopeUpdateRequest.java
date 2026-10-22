package com.gas.forecast.business.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型适用范围更新请求参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelConfigScopeUpdateRequest {
    @NotNull(message = "ID不能为空")
    private Long id;

    @NotNull(message = "启用状态不能为空")
    private Integer enabled;

    @Size(max = 32, message = "区域编码长度不能超过32个字符")
    private String regionCode;

    @Size(max = 32, message = "行业编码长度不能超过32个字符")
    private String industryCode;

    @Size(max = 64, message = "客户编码长度不能超过64个字符")
    private String customerCode;

    private List<@Size(max = 32, message = "区域编码长度不能超过32个字符") String> regionCodes;

    private List<@Size(max = 32, message = "行业编码长度不能超过32个字符") String> industryCodes;

    private List<@Size(max = 64, message = "客户编码长度不能超过64个字符") String> customerCodes;

    private List<FeatureRefItem> featureRefs;

    @Size(max = 16, message = "时间粒度长度不能超过16个字符")
    private String timeGranularity;

    @Min(value = 1, message = "最小预测周期必须大于0")
    private Integer minForecastHorizon;

    @Min(value = 1, message = "最大预测周期必须大于0")
    private Integer maxForecastHorizon;

    @Min(value = 1, message = "最短训练数据量必须大于0")
    private Integer minimumTrainingRows;

    @Min(value = 1, message = "推荐训练周期数必须大于0")
    private Integer recommendedTrainingPeriods;

    @Size(max = 16, message = "默认训练范围策略长度不能超过16个字符")
    private String defaultTrainingRangeMode;

    @Min(value = 1, message = "自定义训练周期数必须大于0")
    private Integer defaultTrainingPeriods;

    @Size(max = 500, message = "训练数据要求长度不能超过500个字符")
    private String trainingDataRequirement;

    public Long id() {
        return id;
    }

    public String regionCode() {
        return regionCode;
    }

    public String industryCode() {
        return industryCode;
    }

    public String customerCode() {
        return customerCode;
    }

    public List<String> regionCodes() {
        return regionCodes;
    }

    public List<String> industryCodes() {
        return industryCodes;
    }

    public List<String> customerCodes() {
        return customerCodes;
    }

    public List<FeatureRefItem> featureRefs() {
        return featureRefs;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeatureRefItem {
        @NotNull(message = "特征ID不能为空")
        private Long featureId;

        @Size(max = 32, message = "模型输出特征编码长度不能超过32个字符")
        private String outputFeatureCode;

        private Integer requiredFlag;

        private Integer featureOrder;

        public Long featureId() {
            return featureId;
        }

        public Integer requiredFlag() {
            return requiredFlag;
        }

        public Integer featureOrder() {
            return featureOrder;
        }

        public String outputFeatureCode() {
            return outputFeatureCode;
        }
    }
}
