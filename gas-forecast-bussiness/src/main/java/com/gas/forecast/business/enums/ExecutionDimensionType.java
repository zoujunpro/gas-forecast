package com.gas.forecast.business.enums;

import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;

/** 训练或预测任务的最小执行维度。 */
@Getter
public enum ExecutionDimensionType {
    /** 一个具体客户为一个最小执行目标。 */
    CUSTOMER("CUSTOMER"),
    /** 一个地区为一个最小执行目标。 */
    REGION("REGION"),
    /** 一个行业（可附带地区范围）为一个最小执行目标。 */
    INDUSTRY("INDUSTRY");

    private final String code;

    ExecutionDimensionType(String code) {
        this.code = code;
    }

    public static Optional<ExecutionDimensionType> fromCode(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equals(code)).findFirst();
    }
}
