package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.util.Date;
import lombok.Data;

/**
 * 模型训练结果明细。
 *
 * @TableName model_train_record_tb
 */
@TableName(value = "model_train_record_tb")
@Data
public class ModelTrainRecordTb {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String batchNo;

    private String agentCode;

    private String regionCode;

    private String regionName;

    private String customerCode;

    private String customerName;

    private String industryCode;

    private String industryName;

    private String trainStartDate;

    private String trainEndDate;

    private String status;

    private String bestModel;

    private BigDecimal mape;

    private BigDecimal wmape;

    private BigDecimal smape;

    private BigDecimal rmse;

    private BigDecimal mae;

    private BigDecimal r2;

    private BigDecimal trainDurationSeconds;

    private Object resultJson;

    private String errorMessage;

    private String createdBy;

    private String createdByName;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date updatedAt;

    private String modelVersion;

    private byte[] requestParam;
}
