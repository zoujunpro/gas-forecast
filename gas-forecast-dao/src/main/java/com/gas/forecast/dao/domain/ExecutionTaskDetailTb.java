package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/** 一个任务下按最小执行目标展开的任务明细。 */
@Data
@TableName("execution_task_detail_tb")
public class ExecutionTaskDetailTb {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 父任务业务编号，对应 execution_task_tb.task_no。 */
    private String taskNo;
    /** 对外可读明细编号，例如 TASK-20261022-00000001-000001。 */
    private String detailNo;
    /** 执行智能体类型。 */
    private String agentCode;
    /** 最小执行维度：CUSTOMER / REGION / INDUSTRY。 */
    private String dimensionType;
    private String regionCode;
    private String industryCode;
    private String customerCode;
    /** 该目标的训练批次号，训练发起后回填。 */
    private String trainBatchNo;
    /** 该目标的预测批次号，预测发起后回填。 */
    private String forecastBatchNo;
    /** 最小目标的执行状态。 */
    private String status;
    private Date createdAt;
    private Date updatedAt;
}
