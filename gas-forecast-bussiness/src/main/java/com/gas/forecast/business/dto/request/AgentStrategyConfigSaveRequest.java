package com.gas.forecast.business.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentStrategyConfigSaveRequest {
    private Long id;
    @NotBlank
    @Size(max = 64)
    private String agentCode;
    @NotBlank
    @Size(max = 128)
    private String agentName;
    @NotBlank
    @Size(max = 32)
    private String trainScopeType;
    @NotBlank
    @Size(max = 32)
    private String triggerType;
    @NotBlank
    @Size(max = 64)
    private String checkCron;
    @NotNull
    @Min(0)
    private Integer dataDelayPeriods;
    @NotNull
    @Min(1)
    private Integer modelValidityPeriods;
    @NotNull
    @Min(0)
    private Integer maxRetryCount;
    @NotNull
    @Min(1)
    private Integer retryIntervalMinutes;
    @NotNull
    private Integer autoTrainEnabled;
    @NotNull
    private Integer autoForecastEnabled;
    @NotNull
    @Min(0)
    private Integer forecastLeadPeriods;
    @NotNull
    private Integer enabled;
    @Size(max = 500)
    private String description;
}
