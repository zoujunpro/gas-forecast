package com.gas.forecast.business.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DailyWeatherPageRequest {
    @Min(1)
    private Integer page;
    @Min(1)
    private Integer size;
    @Size(max = 128)
    private String keyword;
    @Size(max = 64)
    private String regionCode;
    @Size(max = 16)
    private String weatherType;
    @Size(max = 16)
    private String qualityStatus;
    @Size(max = 10)
    private String statDateStart;
    @Size(max = 10)
    private String statDateEnd;
}
