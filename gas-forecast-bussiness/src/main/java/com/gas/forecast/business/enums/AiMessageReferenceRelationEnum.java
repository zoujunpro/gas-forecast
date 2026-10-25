package com.gas.forecast.business.enums;

import java.util.Arrays;
import lombok.Getter;

/** AI 消息与业务对象的关联关系。 */
@Getter
public enum AiMessageReferenceRelationEnum {
    CREATED("CREATED"), REFERENCED("REFERENCED"), DISPLAYED("DISPLAYED");

    private final String code;

    AiMessageReferenceRelationEnum(String code) { this.code = code; }

    public static boolean contains(String value) {
        return Arrays.stream(values()).anyMatch(item -> item.code.equals(value));
    }
}
