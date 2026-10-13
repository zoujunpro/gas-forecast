package com.gas.forecast.business.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DailyWeatherUpdateRequest extends DailyWeatherCreateRequest {
    @NotNull
    private Long id;
}
