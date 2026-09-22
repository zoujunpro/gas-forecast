package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.util.Date;
import lombok.Data;

/**
 * 旬度预测结果
 * @TableName model_forecast_result_tb
 */
@TableName(value ="model_forecast_result_tb")
@Data
public class ModelForecastResultTb {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 预测批次
     */
    private String forecastBatchNo;

    /**
     * 预测旬日期
     */
    private String forecastDate;

    /**
     * 预测值
     */
    private BigDecimal forecastValue;

    /**
     * 创建时间
     */
    private Date createdAt;

}
