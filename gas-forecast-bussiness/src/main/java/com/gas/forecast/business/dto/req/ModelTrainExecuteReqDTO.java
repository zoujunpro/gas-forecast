package com.gas.forecast.business.dto.req;

import jakarta.validation.constraints.Size;

/**
 * 模型训练执行请求。
 */
public record ModelTrainExecuteReqDTO(
        @Size(max = 64, message = "训练配置编码长度不能超过64个字符")
        String trainCode,

        @Size(max = 64, message = "训练配置编码长度不能超过64个字符")
        String configCode
) {
}
