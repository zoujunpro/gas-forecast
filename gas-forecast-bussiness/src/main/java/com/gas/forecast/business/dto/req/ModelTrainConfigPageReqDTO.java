package com.gas.forecast.business.dto.req;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 模型训练配置分页查询请求参数。
 */
public record ModelTrainConfigPageReqDTO(
        @Min(value = 1, message = "页码不能小于1")
        Integer page,

        @Min(value = 1, message = "每页条数不能小于1")
        Integer size,

        @Size(max = 128, message = "搜索关键字长度不能超过128个字符")
        String keyword,

        @Size(max = 64, message = "智能体编码长度不能超过64个字符")
        String agentCode,

        @Size(max = 32, message = "时间格式长度不能超过32个字符")
        String timeGranularity
) {
}
