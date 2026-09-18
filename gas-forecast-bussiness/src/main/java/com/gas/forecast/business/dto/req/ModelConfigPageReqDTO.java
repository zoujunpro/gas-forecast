package com.gas.forecast.business.dto.req;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 模型配置分页查询请求参数。
 */
public record ModelConfigPageReqDTO(
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
         * 搜索关键字，匹配配置编码、名称、智能体或场景。
         */
        @Size(max = 128, message = "搜索关键字长度不能超过128个字符")
        String keyword
) {
}
