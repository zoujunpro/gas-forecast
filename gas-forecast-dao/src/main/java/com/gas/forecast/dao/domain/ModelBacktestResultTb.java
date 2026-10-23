package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.util.Date;
import lombok.Data;

/** 一次统一预测执行产生的回测明细。 */
@Data
@TableName("model_backtest_result_tb")
public class ModelBacktestResultTb {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String forecastNo;
    private String trainDate;
    private BigDecimal actualValue;
    private BigDecimal predictedValue;
    private Date createdAt;
}
