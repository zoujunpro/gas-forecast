package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.util.Date;
import lombok.Data;

/** 一次训练或预测请求对应的一条统一执行任务。 */
@Data
@TableName("execution_task_tb")
public class ExecutionTaskTb {
    /** 主键ID。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 一次用户任务的稳定唯一编号，供任务目标、步骤和事件关联。 */
    private String taskNo;

    /** 所属会话编号。 */
    private String conversationNo;

    /** 业务类型：TRAIN训练、FORECAST预测。 */
    private String businessType;

    /** 执行维度：CUSTOMER客户、REGION地区、INDUSTRY行业。 */
    private String dimensionType;

    /** 任务名称。 */
    private String taskName;

    /** 目标组合范围快照；组内取交集、组间取并集。 */
    private String scopeConditionJson;

    /** 任务状态：PENDING、RUNNING、COMPLETED、FAILED、CANCELLED。 */
    private String status;

    /** 当前执行步骤编码。 */
    private String currentStepCode;

    /** 错误编码。 */
    private String errorCode;

    /** 错误详情。 */
    private String errorMessage;

    /** 任务创建人编码。 */
    private String createdBy;

    /** 开始执行时间。 */
    private Date startedAt;

    /** 结束执行时间。 */
    private Date finishedAt;

    /** 创建时间。 */
    private Date createdAt;

    /** 更新时间。 */
    private Date updatedAt;

    /* 以下字段只保留源代码迁移期的编译兼容性，数据库不再持久化。 */
    @TableField(exist = false)
    private String batchNo;
    @TableField(exist = false)
    private String businessNo;
    @TableField(exist = false)
    private String agentCode;
    @TableField(exist = false)
    private String scopeType;
    @TableField(exist = false)
    private String regionCode;
    @TableField(exist = false)
    private String regionName;
    @TableField(exist = false)
    private String industryCode;
    @TableField(exist = false)
    private String industryName;
    @TableField(exist = false)
    private String customerCode;
    @TableField(exist = false)
    private String customerName;
    @TableField(exist = false)
    private String currentStage;
    @TableField(exist = false)
    private BigDecimal progressPercent;
    @TableField(exist = false)
    private Integer attemptNo;
    @TableField(exist = false)
    private Integer maxAttempts;
    @TableField(exist = false)
    private Date nextRetryAt;
    @TableField(exist = false)
    private String workerId;
    @TableField(exist = false)
    private Date heartbeatAt;
    @TableField(exist = false)
    private Date leaseExpiresAt;
    @TableField(exist = false)
    private String requestJson;
    @TableField(exist = false)
    private String checkpointJson;
    @TableField(exist = false)
    private String resultJson;
    @TableField(exist = false)
    private String idempotencyKey;
}
