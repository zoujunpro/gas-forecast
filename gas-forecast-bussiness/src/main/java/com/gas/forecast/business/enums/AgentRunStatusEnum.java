package com.gas.forecast.business.enums;

import java.util.Arrays;
import lombok.Getter;

/** 单轮 Agent 编排执行状态。 */
@Getter
public enum AgentRunStatusEnum {
    PENDING("PENDING"),
    RUNNING("RUNNING"),
    WAITING("WAITING"),
    SUCCESS("SUCCESS"),
    FAILED("FAILED"),
    CANCELLED("CANCELLED");

    private final String code;

    AgentRunStatusEnum(String code) {
        this.code = code;
    }

    /** 将外部 Agent 返回的状态收敛为受支持的状态码。 */
    public static String normalize(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        return Arrays.stream(values())
                .map(AgentRunStatusEnum::getCode)
                .anyMatch(normalized::equals)
                ? normalized : FAILED.getCode();
    }
}
