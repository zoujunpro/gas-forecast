package com.gas.forecast.business.enums;

import java.util.Arrays;
import lombok.Getter;

/** 可创建 AI 会话的智能体编码。 */
@Getter
public enum AiAgentCodeEnum {
    GENERAL("general"), SHORT_TERM("short-term"), MONTHLY_SALES("monthly-sales"),
    WINTER_SUPPLY("winter-supply");

    private final String code;

    AiAgentCodeEnum(String code) { this.code = code; }

    public static boolean contains(String value) {
        return Arrays.stream(values()).anyMatch(item -> item.code.equals(value));
    }
}
