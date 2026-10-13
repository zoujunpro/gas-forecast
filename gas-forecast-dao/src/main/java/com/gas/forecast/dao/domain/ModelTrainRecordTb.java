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

    /** 关联训练配置ID。 */
    private Long trainConfigId;

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

    /** 活动批次防重键，进入终态后置空。 */
    private String activeDedupKey;

    /** MANUAL/AI/SCHEDULE/DATA_EVENT。 */
    private String triggerSource;

    private Integer priority;

    private Integer retryCount;

    private Integer maxRetryCount;

    private Date nextRetryTime;

    private String claimedBy;

    private Date claimedAt;

    private Date startedAt;

    private Date finishedAt;

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
