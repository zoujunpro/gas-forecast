package com.gas.forecast.business.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.gas.forecast.business.dto.request.ModelTrainAgentTrainRequest;
import java.math.BigDecimal;
import java.util.Date;
import lombok.Data;

/** 单个模型训练批次及其结果。 */
@Data
public class ModelTrainRecordResponse {
    private Long id;
    private String batchNo;
    private String agentCode;
    private String agentName;
    private String regionName;
    private String industryName;
    private String customerName;
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
    private String modelVersion;
    private String errorMessage;
    private String message;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updatedAt;

    private ModelTrainAgentTrainRequest requestJson;
    private ModelTrainAgentTrainResultDTO resultJson;
}
