package com.gas.forecast.business.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentStrategyConfigPageRequest {
    @Min(1)
    private Integer page;
    @Min(1)
    private Integer size;
    @Size(max = 128)
    private String keyword;
}
