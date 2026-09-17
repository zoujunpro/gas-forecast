package com.gas.forecast.business.dto.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 标准销量数据分页查询请求参数。
 */
public record DataSalesPageReqDTO(
        /**
         * 当前页码，从1开始。
         */
        @Min(value = 1, message = "页码不能小于1")
        Integer page,

        /**
         * 每页条数。
         */
        @Min(value = 1, message = "每页条数不能小于1")
        @Max(value = 200, message = "每页条数不能超过200")
        Integer size,

        /**
         * 搜索关键字，匹配区域、行业、客户、客户编码或来源文件。
         */
        @Size(max = 128, message = "搜索关键字长度不能超过128个字符")
        String keyword,

        /**
         * 统计开始日期。
         */
        @Size(max = 32, message = "开始日期长度不能超过32个字符")
        String startDate,

        /**
         * 统计结束日期。
         */
        @Size(max = 32, message = "结束日期长度不能超过32个字符")
        String endDate
) {
}
