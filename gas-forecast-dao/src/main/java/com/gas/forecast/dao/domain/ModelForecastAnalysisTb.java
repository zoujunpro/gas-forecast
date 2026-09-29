package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/** 大模型预测结果分析。 */
@Data
@TableName("model_forecast_analysis_tb")
public class ModelForecastAnalysisTb {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String forecastBatchNo;
    @TableField(select = false)
    private byte[] analysisContent;
    private String llmModel;
    private String analysisStatus;
    private String errorMessage;
    private Date createTime;
    private Date updateTime;
}
