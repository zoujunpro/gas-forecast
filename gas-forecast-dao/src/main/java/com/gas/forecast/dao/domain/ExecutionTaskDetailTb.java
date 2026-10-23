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
    /** 首次实际执行时生成的稳定预测编号；失败重试时保持不变。 */
    private String forecastNo;
    /** 最小目标的执行状态。 */
    private String status;
    private Date createdAt;
    private Date updatedAt;
}
