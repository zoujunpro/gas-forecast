package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

@Data
@TableName("model_train_execution_step_tb")
public class ModelTrainExecutionStepTb {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String trainBatchNo;
    private Integer attemptNo;
    private Long parentStepId;
    private Integer stepLevel;
    private String stepCode;
    private String stepName;
    private Integer stepOrder;
    private String status;
    private String inputSummary;
    private String outputSummary;
    private Integer producedCount;
    private String errorMessage;
    private Date startedAt;
    private Date finishedAt;
    private Long durationMs;
    private Date createdAt;
    private Date updatedAt;
}
