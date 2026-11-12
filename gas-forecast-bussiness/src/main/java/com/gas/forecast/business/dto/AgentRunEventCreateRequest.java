package com.gas.forecast.business.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Python Agent 创建真实过程事件的请求。 */
@Data
public class AgentRunEventCreateRequest {
    @NotBlank
    @Size(max = 64)
    private String eventCode;
    @NotBlank
    @Size(max = 10000)
    private String message;
    @Size(max = 50000)
    private String eventDataJson;
    @Size(max = 32)
    private String status;
    @Size(max = 64)
    private String forecastNo;
    @Size(max = 96)
    private String taskDetailNo;
}
