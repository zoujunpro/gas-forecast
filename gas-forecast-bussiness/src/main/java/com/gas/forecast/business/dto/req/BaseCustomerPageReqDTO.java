package com.gas.forecast.business.dto.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 客户分页查询请求参数。
 */
public record BaseCustomerPageReqDTO(

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
         * 搜索关键字，匹配客户编码、客户名称、区域名称或行业名称。
         */
        @Size(max = 128, message = "搜索关键字长度不能超过128个字符")
        String keyword
) {
}
