package com.gas.forecast.business.enums;

import lombok.Getter;

/** 一个最小执行目标（任务-批次关联记录）的处理状态。 */
@Getter
public enum ExecutionTargetStatus {
    PENDING("PENDING", false),
    TRAINING("TRAINING", false),
    WAITING_FORECAST("WAITING_FORECAST", false),
    FORECASTING("FORECASTING", false),
    SUCCESS("SUCCESS", true),
    FAILED("FAILED", true),
    CANCELLED("CANCELLED", true);

    private final String code;
    private final boolean terminal;

    ExecutionTargetStatus(String code, boolean terminal) {
        this.code = code;
        this.terminal = terminal;
    }
}
