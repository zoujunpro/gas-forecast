package com.gas.forecast.business.enums;

import java.util.Arrays;
import lombok.Getter;

/** AI 会话消息角色。 */
@Getter
public enum AiMessageRoleEnum {
    USER("USER"), ASSISTANT("ASSISTANT"), SYSTEM("SYSTEM"), TOOL("TOOL");

    private final String code;

    AiMessageRoleEnum(String code) { this.code = code; }

    public static boolean contains(String value) {
        return Arrays.stream(values()).anyMatch(item -> item.code.equalsIgnoreCase(value));
    }
}
