package com.gas.forecast.business.enums;

import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;

/** 用户发起执行任务的原始业务意图。 */
@Getter
public enum ExecutionBusinessType {
    /** 用户直接发起模型训练。 */
    TRAIN("TRAIN"),
    /** 用户发起预测；必要时会在同一任务内先完成训练。 */
    FORECAST("FORECAST");

    private final String code;

    ExecutionBusinessType(String code) {
        this.code = code;
    }

    public static Optional<ExecutionBusinessType> fromCode(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equals(code)).findFirst();
    }
}
