package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/** 可审计的智能体动作及任务高层业务阶段，不保存大模型内部思维链。 */
@Data
@TableName("agent_execution_step_tb")
public class AgentExecutionStepTb {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskNo;
    private Integer taskAttemptNo;
    private Long parentStepId;
    private Integer stepLevel;
    private String stepCategory;
    private String stepCode;
    private String stepName;
    private Integer stepOrder;
    private String status;
    private Integer retryable;
    private String businessRefType;
    private String businessRefNo;
    private String inputSummary;
    private String outputSummary;
    private String checkpointJson;
    private String errorCode;
    private String errorMessage;
    private Date startedAt;
    private Date finishedAt;
    private Long durationMs;
    private Date createdAt;
    private Date updatedAt;
}
