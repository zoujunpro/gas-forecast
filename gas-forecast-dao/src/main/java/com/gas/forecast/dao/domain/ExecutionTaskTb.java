package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/** 一次预测请求对应的一条统一执行任务。 */
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

    /** 所属智能体运行编号；供异步执行过程回写会话事件。 */
    private String runNo;

    /** 执行维度：CUSTOMER客户、REGION地区、INDUSTRY行业。 */
    private String dimensionType;

    /** 任务名称。 */
    private String taskName;

    /** 目标组合范围快照；组内取交集、组间取并集。 */
    private String scopeConditionJson;

    /** 发起预测时的完整参数快照，供异步执行器组装数据和调用模型平台。 */
    private String requestParam;

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

}
