package com.gas.forecast.business.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 训练特征数据新增请求参数。
 */
public record ModelTrainFeatureDataCreateReqDTO(
        @NotBlank(message = "统计日期不能为空")
        @Size(max = 32, message = "统计日期长度不能超过32个字符")
        String statDate,

        @NotBlank(message = "时间粒度不能为空")
        @Size(max = 16, message = "时间粒度长度不能超过16个字符")
        String timeGranularity,

        @Size(max = 64, message = "区域编码长度不能超过64个字符")
        String regionCode,

        @Size(max = 128, message = "区域名称长度不能超过128个字符")
        String regionName,

        @Size(max = 64, message = "客户编码长度不能超过64个字符")
        String customerCode,

        @Size(max = 128, message = "客户名称长度不能超过128个字符")
        String customerName,

        @Size(max = 64, message = "行业编码长度不能超过64个字符")
        String industryCode,

        @Size(max = 128, message = "行业名称长度不能超过128个字符")
        String industryName,

        BigDecimal gasSales,
        Double feature001,
        Double feature002,
        Double feature003,
        Double feature004,
        Double feature005,
        Double feature006,
        Double feature007,
        Double feature008,
        Double feature009,
        Double feature010
) {
}
