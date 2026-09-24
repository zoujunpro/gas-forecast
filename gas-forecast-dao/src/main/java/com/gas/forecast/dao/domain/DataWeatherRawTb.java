package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 原始天气表
 *
 * @TableName data_weather_raw_tb
 */
@TableName(value = "data_weather_raw_tb")
@Data
public class DataWeatherRawTb {
    /**
     *
     */
    @TableId
    private Long id;
}
