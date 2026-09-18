package com.gas.forecast.business.dto.req;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 特征定义分页查询请求参数。
 */
public record ModelFeatureDefinitionPageReqDTO(
        @Min(value = 1, message = "页码不能小于1")
        Integer page,

        @Min(value = 1, message = "每页条数不能小于1")
        Integer size,

        @Size(max = 128, message = "搜索关键字长度不能超过128个字符")
        String keyword
) {
}
