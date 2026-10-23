package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.util.Date;
import lombok.Data;

/**
 * 预测结果明细。
 *
 * @TableName model_forecast_result_tb
 */
@TableName(value = "model_forecast_result_tb")
@Data
public class ModelForecastResultTb {
    /**
     *
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 统一预测编号。 */
    private String forecastNo;

    /**
     * 预测日期
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
