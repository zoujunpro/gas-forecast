package com.gas.forecast.business.enums;

import lombok.Getter;

/** 一个最小执行目标（任务-批次关联记录）的处理状态。 */
@Getter
public enum ExecutionTargetStatusEnum {
    PENDING("PENDING", false),
    /** 统一预测执行器已领取该任务明细。 */
    RUNNING("RUNNING", false),
    /** 已失败任务重新进入统一预测执行器。 */
    RETRYING("RETRYING", false),
    TRAINING("TRAINING", false),
    WAITING_FORECAST("WAITING_FORECAST", false),
    FORECASTING("FORECASTING", false),
    SUCCESS("SUCCESS", true),
    FAILED("FAILED", true),
    CANCELLED("CANCELLED", true);

    private final String code;
    private final boolean terminal;

    ExecutionTargetStatusEnum(String code, boolean terminal) {
        this.code = code;
        this.terminal = terminal;
    }
}
