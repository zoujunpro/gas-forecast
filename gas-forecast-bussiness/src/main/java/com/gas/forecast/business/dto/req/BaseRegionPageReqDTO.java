package com.gas.forecast.business.dto.req;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 区域分页查询请求参数。
 */
public record BaseRegionPageReqDTO(
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
         * 搜索关键字，匹配区域编码、区域名称或区域类型。
         */
        @Size(max = 128, message = "搜索关键字长度不能超过128个字符")
        String keyword
) {
}
