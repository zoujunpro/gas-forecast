package com.gas.forecast.business.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 更新同一条智能体过程事件的状态和展示文案。 */
@Data
public class AgentRunEventUpdateRequest {
    @NotBlank @Size(max = 32)
    private String status;
    @NotBlank @Size(max = 10000)
    private String message;
    @Size(max = 50000)
    private String eventDataJson;
    @Size(max = 64)
    private String errorCode;
    @Size(max = 10000)
    private String errorMessage;
}
