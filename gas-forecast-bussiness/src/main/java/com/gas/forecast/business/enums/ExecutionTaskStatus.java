package com.gas.forecast.business.enums;

import lombok.Getter;

/** 用户一次训练或预测请求的整体任务状态。 */
@Getter
public enum ExecutionTaskStatus {
    PENDING("PENDING", false),
    RUNNING("RUNNING", false),
    COMPLETED("COMPLETED", true),
    FAILED("FAILED", true),
    CANCELLED("CANCELLED", true);

    private final String code;
    private final boolean terminal;

    ExecutionTaskStatus(String code, boolean terminal) {
        this.code = code;
        this.terminal = terminal;
    }
}
