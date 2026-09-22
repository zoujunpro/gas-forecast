package com.gas.forecast.business.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 模型配置新增请求参数。
 */
public record ModelConfigCreateReqDTO(
        @NotBlank(message = "配置编码不能为空")
        @Size(max = 64, message = "配置编码长度不能超过64个字符")
        String configCode,

        @NotBlank(message = "配置名称不能为空")
        @Size(max = 128, message = "配置名称长度不能超过128个字符")
        String configName,

        @Size(max = 32, message = "模型版本长度不能超过32个字符")
        String modelVersion,

        @NotBlank(message = "智能体编码不能为空")
        @Size(max = 64, message = "智能体编码长度不能超过64个字符")
        String agentCode,

        @NotBlank(message = "场景编码不能为空")
        @Size(max = 32, message = "场景编码长度不能超过32个字符")
        String sceneCode,

        @Size(max = 32, message = "区域编码长度不能超过32个字符")
        String regionCode,

        @Size(max = 32, message = "行业编码长度不能超过32个字符")
        String industryCode,

        @Size(max = 64, message = "客户编码长度不能超过64个字符")
        String customerCode,

        List<@Size(max = 32, message = "区域编码长度不能超过32个字符") String> regionCodes,

        List<@Size(max = 32, message = "行业编码长度不能超过32个字符") String> industryCodes,

        List<@Size(max = 64, message = "客户编码长度不能超过64个字符") String> customerCodes,

        @Size(max = 500, message = "描述长度不能超过500个字符")
        String description
) {
}
