package com.gas.forecast.business.enums;

import java.util.Arrays;
import lombok.Getter;

/** AI 发起澄清或确认的原因。 */
@Getter
public enum AiClarificationReasonEnum {
    INTENT_AMBIGUOUS("INTENT_AMBIGUOUS"),
    ENTITY_MISSING("ENTITY_MISSING"),
    ENTITY_AMBIGUOUS("ENTITY_AMBIGUOUS"),
    ENTITY_NOT_FOUND("ENTITY_NOT_FOUND"),
    PARAMETER_MISSING("PARAMETER_MISSING"),
    PARAMETER_CONFLICT("PARAMETER_CONFLICT"),
    SCOPE_AMBIGUOUS("SCOPE_AMBIGUOUS"),
    REFERENCE_AMBIGUOUS("REFERENCE_AMBIGUOUS"),
    WRITE_CONFIRMATION_REQUIRED("WRITE_CONFIRMATION_REQUIRED");

    private final String code;

    AiClarificationReasonEnum(String code) { this.code = code; }

    public static boolean contains(String value) {
        return Arrays.stream(values()).anyMatch(item -> item.code.equals(value));
    }
}
