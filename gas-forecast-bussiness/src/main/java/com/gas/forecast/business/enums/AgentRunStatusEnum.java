package com.gas.forecast.business.enums;

import java.util.Arrays;
import lombok.Getter;

/** 单轮 Agent 编排执行状态。 */
@Getter
public enum AgentRunStatusEnum {
    PENDING("PENDING", "等待执行"),
    RUNNING("RUNNING", "正在执行"),
    WAITING("WAITING", "等待用户补充或确认信息"),
    SUCCESS("SUCCESS", "执行成功"),
    FAILED("FAILED", "执行失败"),
    CANCELLED("CANCELLED", "执行已取消");

    private final String code;
    private final String description;

    AgentRunStatusEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /** 将外部 Agent 返回的状态收敛为受支持的状态码。 */
    public static String normalize(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        return Arrays.stream(values()).map(AgentRunStatusEnum::getCode).anyMatch(normalized::equals) ? normalized : FAILED.getCode();
    }
}
