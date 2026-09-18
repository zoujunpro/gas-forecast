package com.gas.forecast.business.dto.req;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 原始数据文件分页查询请求参数。
 */
public record DataFileInfoPageReqDTO(
        /**
         * 当前页码，从1开始。
         */
        @Min(value = 1, message = "页码不能小于1")
        Integer page,

        /**
         * 每页条数。
         */
        @Min(value = 1, message = "每页条数不能小于1")
        Integer size,

        /**
         * 搜索关键字，匹配文件名、对象Key、文件Hash、状态或创建人。
         */
        @Size(max = 128, message = "搜索关键字长度不能超过128个字符")
        String keyword
) {
}
