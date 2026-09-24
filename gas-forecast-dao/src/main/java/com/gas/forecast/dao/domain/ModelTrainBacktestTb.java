package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.util.Date;
import lombok.Data;

/**
 * 模型训练滚动回测明细表
 *
 * @TableName model_train_backtest_tb
 */
@TableName(value = "model_train_backtest_tb")
@Data
public class ModelTrainBacktestTb {
    /**
     *
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 训练批次号
     */
    private String trainBatchNo;

    /**
     * 回测日期
     */
    private Date trainDate;

    /**
     * 历史实际值
     */
    private BigDecimal actualValue;

    /**
     * 回测预测值
     */
    private BigDecimal predictedValue;

    /**
     * 创建日期
     */
    private Date createdAt;
}
