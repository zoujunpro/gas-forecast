package com.gas.forecast.business.dto.req;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 模型适用范围更新请求参数。
 */
public record ModelConfigScopeUpdateReqDTO(
        @NotNull(message = "ID不能为空")
        Long id,

        @Size(max = 32, message = "区域编码长度不能超过32个字符")
        String regionCode,

        @Size(max = 32, message = "行业编码长度不能超过32个字符")
        String industryCode,

        @Size(max = 64, message = "客户编码长度不能超过64个字符")
        String customerCode,

        List<@Size(max = 32, message = "区域编码长度不能超过32个字符") String> regionCodes,

        List<@Size(max = 32, message = "行业编码长度不能超过32个字符") String> industryCodes,

        List<@Size(max = 64, message = "客户编码长度不能超过64个字符") String> customerCodes,

        List<FeatureRefItem> featureRefs
) {
    public record FeatureRefItem(
            @NotNull(message = "特征ID不能为空")
            Long featureId,

            Integer requiredFlag,

            Integer featureOrder
    ) {
    }
}
