package com.gas.forecast.business.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.util.Date;
import lombok.Data;

@Data
public class DailyWeatherResponse {
    private Long id;
    private String regionCode;
    private String regionName;
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date statDate;
    private String statMonth;
    private String statTenday;
    private String weatherType;
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
    private String sourceCode;
    private String qualityStatus;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updatedAt;
}
