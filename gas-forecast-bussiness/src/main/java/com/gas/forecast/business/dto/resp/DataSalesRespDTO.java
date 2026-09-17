package com.gas.forecast.business.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Date;

/**
 * 标准销量数据应答参数。
 */
public record DataSalesRespDTO(
        /**
         * 数据ID。
         */
        Long id,

        /**
         * 统计日期。
         */
        String statDate,

        /**
         * 区域编码。
         */
        String regionCode,

        /**
         * 区域名称。
         */
        String regionName,

        /**
         * 行业编码。
         */
        String industryCode,

        /**
         * 行业名称。
         */
        String industryName,

        /**
         * 客户编码。
         */
        String customerCode,

        /**
         * 客户名称。
         */
        String customerName,

        /**
         * 销量值。
         */
        String gasSales,

        /**
         * 来源文件。
         */
        String fileId,

        /**
         * 创建时间。
         */
        @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
        Date createdAt
) {
}
