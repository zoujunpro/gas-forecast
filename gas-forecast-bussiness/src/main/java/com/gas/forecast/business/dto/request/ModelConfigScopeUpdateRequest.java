package com.gas.forecast.business.dto.request;

import jakarta.validation.constraints.NotNull;
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
    }
}
