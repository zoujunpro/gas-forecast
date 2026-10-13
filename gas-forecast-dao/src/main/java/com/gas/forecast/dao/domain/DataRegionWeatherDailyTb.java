package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.util.Date;
import lombok.Data;

@Data
@TableName("data_region_weather_daily_tb")
public class DataRegionWeatherDailyTb {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String regionCode;
    private String regionName;
    private Date statDate;
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private String statMonth;
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private String statTenday;
    private String weatherType;
    private String weatherVersion;
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
    private Date createdAt;
    private Date updatedAt;
}
