package com.gas.forecast.business.enums;

import lombok.Getter;

/** 模型训练任务状态。 */
@Getter
public enum ModelTrainStatus {
    PENDING("PENDING", false),
    RUNNING("RUNNING", false),
    SUCCESS("SUCCESS", true),
    FAILED("FAILED", true);

    private final String code;
    private final boolean terminal;

    ModelTrainStatus(String code, boolean terminal) {
        this.code = code;
        this.terminal = terminal;
    }
}
