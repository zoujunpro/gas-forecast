package com.gas.forecast.business.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Date;

/**
 * 客户基础信息应答参数。
 */
public record BaseCustomerRespDTO(
        /**
         * 客户ID。
         */
        Long id,

        /**
         * 客户编码。
         */
        String customerCode,

        /**
         * 客户名称。
         */
        String customerName,

        /**
         * 所属行业编码。
         */
        String industryCode,

        /**
         * 所属行业名称。
         */
        String industryName,

        /**
         * 所属区域编码。
         */
        String regionCode,

        /**
         * 所属区域名称。
         */
        String regionName,

        /**
         * 原始区域名称。
         */
        String rawRegionName,

        /**
         * 原始行业名称。
         */
        String rawIndustryName,

        /**
         * 创建时间。
         */
        @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
        Date createdAt,

        /**
         * 更新时间。
         */
        @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
        Date updatedAt
) {
}
