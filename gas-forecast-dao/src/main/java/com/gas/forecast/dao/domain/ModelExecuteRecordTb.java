package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import lombok.Data;

/**
 * 一次预测任务明细的模型执行记录。
 *
 * <p>训练、回测与未来预测共享 {@code forecastNo}，模型产物保存于该编号对应的目录。</p>
 */
@Data
@TableName("model_execute_record_tb")
public class ModelExecuteRecordTb {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String forecastNo;
    private Long taskDetailId;
    private String agentCode;
    private String modelCode;
    private String modelVersion;
    private String regionCode;
    private String regionName;
    private String industryCode;
    private String industryName;
    private String customerCode;
    private String customerName;
    private LocalDate historyStartDate;
    private LocalDate historyEndDate;
    private Integer historyRows;
    private LocalDate forecastStartDate;
    private Integer forecastHorizon;
    private String forecastUnit;
    private String status;
    private String bestModel;
    private Integer candidateCount;
    private Integer successfulCandidateCount;
    private BigDecimal mape;
    private BigDecimal wmape;
    private BigDecimal smape;
    private BigDecimal rmse;
    private BigDecimal mae;
    private BigDecimal r2;
    private BigDecimal executeDurationSeconds;

    /** 模型平台统一执行请求快照 JSON 文本。 */
    private String requestParam;
    /** 模型平台返回的训练结果快照 JSON 文本。 */
    private String trainingResultJson;
    /** 模型平台返回的回测结果快照 JSON 文本。 */
    private String backtestResultJson;
    /** 模型平台返回的预测结果快照 JSON 文本。 */
    private String forecastResultJson;
    private String errorMessage;
    private String createdBy;
    private String createdByName;
    private Date startedAt;
    private Date finishedAt;
    private Date createdAt;
    private Date updatedAt;
}
