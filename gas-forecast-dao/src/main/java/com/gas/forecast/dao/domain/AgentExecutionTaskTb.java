package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.util.Date;
import lombok.Data;

/** 智能体、MCP或管理后台发起的一次完整训练/预测业务任务。 */
@Data
@TableName("agent_execution_task_tb")
public class AgentExecutionTaskTb {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskNo;
    private String parentTaskNo;
    private String conversationNo;
    private String taskType;
    private String triggerSource;
    private String agentCode;
    private String scopeType;
    private String regionCode;
    private String regionName;
    private String industryCode;
    private String industryName;
    private String customerCode;
    private String customerName;
    private String status;
    private String currentStage;
    private String currentStepCode;
    private BigDecimal progressPercent;
    private Integer taskAttemptNo;
    private String latestTrainBatchNo;
    private String latestForecastBatchNo;
    private String idempotencyKey;
    private String requestJson;
    private String checkpointJson;
    private String resultJson;
    private String errorCode;
    private String errorMessage;
    private Integer retryable;
    private String createdBy;
    private String createdByName;
    private Date createdAt;
    private Date startedAt;
    private Date finishedAt;
    private Date updatedAt;
}
