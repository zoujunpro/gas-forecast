package com.gas.forecast.business.enums;

import java.util.Arrays;
import lombok.Getter;

/** 澄清字段的输入方式。 */
@Getter
public enum AiClarificationInputTypeEnum {
    TEXT("TEXT"), NUMBER("NUMBER"), DATE("DATE"), SELECT("SELECT"),
    MULTI_SELECT("MULTI_SELECT"), CONFIRM("CONFIRM");

    private final String code;

    AiClarificationInputTypeEnum(String code) { this.code = code; }

    public static boolean contains(String value) {
        return Arrays.stream(values()).anyMatch(item -> item.code.equals(value));
    }
}
