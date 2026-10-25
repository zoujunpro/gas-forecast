package com.gas.forecast.business.enums;

import java.util.Arrays;
import lombok.Getter;

/** AI 消息可关联的业务对象类型。 */
@Getter
public enum AiMessageReferenceTypeEnum {
    EXECUTION_TASK("EXECUTION_TASK"), FORECAST("FORECAST");

    private final String code;

    AiMessageReferenceTypeEnum(String code) { this.code = code; }

    public static boolean contains(String value) {
        return Arrays.stream(values()).anyMatch(item -> item.code.equals(value));
    }
}
