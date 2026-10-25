package com.gas.forecast.business.enums;

import java.util.Arrays;
import lombok.Getter;

/** AI 等待用户回复时的交互类型。 */
@Getter
public enum AiInteractionTypeEnum {
    CLARIFICATION("CLARIFICATION"), CONFIRMATION("CONFIRMATION");

    private final String code;

    AiInteractionTypeEnum(String code) { this.code = code; }

    public static boolean contains(String value) {
        return Arrays.stream(values()).anyMatch(item -> item.code.equals(value));
    }
}
