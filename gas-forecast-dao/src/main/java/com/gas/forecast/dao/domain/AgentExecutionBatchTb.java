package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/** 统一任务与每次真实训练或预测批次的不可变关联历史。 */
@Data
@TableName("agent_execution_batch_tb")
public class AgentExecutionBatchTb {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskNo;
    private Integer taskAttemptNo;
    private String stage;
    private String batchType;
    private String batchNo;
    private Integer batchAttemptNo;
    private String previousBatchNo;
    private String retryMode;
    private String triggerSource;
    private String status;
    private String errorMessage;
    private String createdBy;
    private String createdByName;
    private Date createdAt;
    private Date startedAt;
    private Date finishedAt;
    private Date updatedAt;
}
