package com.gas.forecast.business.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 智能体页面按已选业务范围直接发起预测，不经过AI意图识别。 */
@Data
public class ScopedForecastStartRequest {
    @NotBlank(message = "智能体类型不能为空")
    @Size(max = 64)
    private String agentCode;
    @NotBlank(message = "客户编码不能为空")
    @Size(max = 64)
    private String customerCode;
    @Size(max = 64)
    private String regionCode;
    @Size(max = 64)
    private String industryCode;
    @Min(value = 1, message = "预测周期必须大于0")
    @Max(value = 365, message = "预测周期不能超过365期")
    private Integer forecastHorizon;
    @Size(max = 16)
    private String forecastFrequency;
    @Size(max = 36)
    private String idempotencyKey;
}
