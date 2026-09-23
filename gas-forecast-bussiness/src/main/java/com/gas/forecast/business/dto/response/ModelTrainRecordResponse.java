package com.gas.forecast.business.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.gas.forecast.business.dto.request.ModelTrainAgentTrainRequest;
import java.math.BigDecimal;
import java.util.Date;
import lombok.Data;

/** 单个模型训练批次及其结果。 */
@Data
public class ModelTrainRecordResponse {
    /**
     * ID。
     */
    private Long id;
    /**
     * 批次号。
     */
    private String batchNo;
    /**
     * 智能体编码。
     */
    private String agentCode;
    /**
     * 智能体名称。
     */
    private String agentName;
    /**
     * 区域名称。
     */
    private String regionName;
    /**
     * 行业名称。
     */
    private String industryName;
    /**
     * 客户名称。
     */
    private String customerName;
    /**
     * 训练开始日期。
     */
    private String trainStartDate;
    /**
     * 训练结束日期。
     */
    private String trainEndDate;
    /**
     * 状态。
     */
    private String status;
    /**
     * 最优模型。
     */
    private String bestModel;
    /**
     * MAPE指标。
     */
    private BigDecimal mape;
    /**
     * WMAPE指标。
     */
    private BigDecimal wmape;
    /**
     * SMAPE指标。
     */
    private BigDecimal smape;
    /**
     * RMSE指标。
     */
    private BigDecimal rmse;
    /**
     * MAE指标。
     */
    private BigDecimal mae;
    /**
     * R方指标。
     */
    private BigDecimal r2;
    /**
     * 训练耗时秒数。
     */
    private BigDecimal trainDurationSeconds;
    /**
     * 模型版本。
     */
    private String modelVersion;
    /**
     * 错误信息。
     */
    private String errorMessage;
    /**
     * 应答消息。
     */
    private String message;

    /**
     * 创建时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;

    /**
     * 更新时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updatedAt;

    /**
     * 训练请求报文。
     */
    private ModelTrainAgentTrainRequest requestJson;
    /**
     * 训练结果报文。
     */
    private ModelTrainAgentTrainResultDTO resultJson;
}
