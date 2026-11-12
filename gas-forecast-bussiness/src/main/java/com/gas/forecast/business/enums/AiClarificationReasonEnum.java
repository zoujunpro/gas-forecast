package com.gas.forecast.business.enums;

import java.util.Arrays;
import lombok.Getter;

/** AI 发起澄清或确认的原因。 */
@Getter
public enum AiClarificationReasonEnum {
    INTENT_AMBIGUOUS("INTENT_AMBIGUOUS", "用户意图不明确"),
    ENTITY_MISSING("ENTITY_MISSING", "缺少业务实体"),
    ENTITY_AMBIGUOUS("ENTITY_AMBIGUOUS", "业务实体存在多个匹配结果"),
    ENTITY_NOT_FOUND("ENTITY_NOT_FOUND", "未找到对应的业务实体"),
    PARAMETER_MISSING("PARAMETER_MISSING", "缺少必要业务参数"),
    PARAMETER_CONFLICT("PARAMETER_CONFLICT", "业务参数之间存在冲突"),
    SCOPE_AMBIGUOUS("SCOPE_AMBIGUOUS", "业务范围不明确"),
    REFERENCE_AMBIGUOUS("REFERENCE_AMBIGUOUS", "用户指代的对象不明确"),
    WRITE_CONFIRMATION_REQUIRED("WRITE_CONFIRMATION_REQUIRED", "执行写操作前需要用户确认");

    private final String code;
    private final String description;

    AiClarificationReasonEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static boolean contains(String value) {
        return Arrays.stream(values()).anyMatch(item -> item.code.equals(value));
    }
}
