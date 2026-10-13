package com.gas.forecast.business.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Date;
import lombok.Data;

@Data
public class DailyWeatherCreateRequest {
    @NotBlank
    @Size(max = 64)
    private String regionCode;
    @Size(max = 64)
    private String regionName;
    @NotNull
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date statDate;
    @NotBlank
    @Size(max = 16)
    private String weatherType;
    @Size(max = 32)
    private String weatherVersion;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date forecastIssueTime;
    private BigDecimal tempMax;
    private BigDecimal tempMin;
    private BigDecimal tempAvg;
    private BigDecimal feelsLikeMax;
    private BigDecimal feelsLikeMin;
    private BigDecimal feelsLikeAvg;
    private BigDecimal dewPoint;
    private BigDecimal humidity;
    private BigDecimal precipitation;
    private BigDecimal windGust;
    private BigDecimal windSpeed;
    private BigDecimal seaLevelPressure;
    private BigDecimal cloudCover;
    private BigDecimal visibility;
    private BigDecimal solarRadiation;
    private BigDecimal solarEnergy;
    private BigDecimal uvIndex;
    private BigDecimal hdd;
    @NotBlank
    @Size(max = 32)
    private String sourceCode;
    @NotBlank
    @Size(max = 16)
    private String qualityStatus;
}
