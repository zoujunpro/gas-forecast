package com.gas.forecast.business.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 模型训练配置更新请求参数。
 */
public record ModelTrainConfigUpdateReqDTO(
        @NotNull(message = "模型训练配置ID不能为空")
        Long id,

        @NotBlank(message = "训练配置编码不能为空")
        @Size(max = 64, message = "训练配置编码长度不能超过64个字符")
        String trainCode,

        @NotBlank(message = "训练配置名称不能为空")
        @Size(max = 128, message = "训练配置名称长度不能超过128个字符")
        String trainName,

        @NotBlank(message = "智能体编码不能为空")
        @Size(max = 64, message = "智能体编码长度不能超过64个字符")
        String agentCode,

        @Size(max = 64, message = "所属模型编码长度不能超过64个字符")
        String modelCode,

        @Size(max = 128, message = "所属模型名称长度不能超过128个字符")
        String modelName,

        @Size(max = 64, message = "区域编码长度不能超过64个字符")
        String regionCode,

        @Size(max = 64, message = "区域名称长度不能超过64个字符")
        String regionName,

        @Size(max = 64, message = "行业编码长度不能超过64个字符")
        String industryCode,

        @Size(max = 64, message = "行业名称长度不能超过64个字符")
        String industryName,

        @Size(max = 64, message = "客户编码长度不能超过64个字符")
        String customerCode,

        @Size(max = 128, message = "客户名称长度不能超过128个字符")
        String customerName,

        @Size(max = 32, message = "训练开始日期长度不能超过32个字符")
        String trainStartDate,

        @Size(max = 32, message = "训练结束日期长度不能超过32个字符")
        String trainEndDate,

        @Size(max = 32, message = "训练方式长度不能超过32个字符")
        String trainMode,

        @Size(max = 32, message = "时间格式长度不能超过32个字符")
        String timeGranularity,

        Integer recentPeriods,

        @NotNull(message = "启用状态不能为空")
        Integer enabled,

        @Size(max = 512, message = "备注长度不能超过512个字符")
        String remark
) {
}
