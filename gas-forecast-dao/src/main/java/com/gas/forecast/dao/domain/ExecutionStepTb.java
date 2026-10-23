package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.util.Date;
import lombok.Data;

/** 统一执行任务在某次尝试中的过程步骤。 */
@Data
@TableName("execution_step_tb")
public class ExecutionStepTb {
    /** 主键ID。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 执行步骤唯一编号。 */
    private String stepNo;

    /** 所属任务明细业务编号，关联 execution_task_detail_tb.detail_no。 */
    private String taskDetailNo;

    /** 任务执行次数，首次为1。 */
    private Integer attemptNo;

    /** 步骤编码。 */
    private String stepCode;

    /** 步骤名称。 */
    private String stepName;

    /** 步骤执行顺序。 */
    private Integer stepOrder;

    /** 步骤状态：PENDING、RUNNING、SUCCESS、FAILED、SKIPPED、CANCELLED。 */
    private String status;

    /** 当前步骤执行进度，范围0到100。 */
    private BigDecimal progressPercent;

    /** 步骤输入参数摘要JSON。 */
    private String inputJson;

    /** 步骤输出结果摘要JSON。 */
    private String outputJson;

    /** 步骤执行指标JSON。 */
    private String metricsJson;

    /** 供前端展示的步骤执行说明。 */
    private String message;

    /** 步骤失败错误编码。 */
    private String errorCode;

    /** 步骤失败原因。 */
    private String errorMessage;

    /** 步骤开始执行时间。 */
    private Date startedAt;

    /** 步骤结束时间。 */
    private Date finishedAt;

    /** 步骤真实执行耗时，单位毫秒。 */
    private Long durationMs;

    /** 创建时间。 */
    private Date createdAt;

    /** 更新时间。 */
    private Date updatedAt;
}
