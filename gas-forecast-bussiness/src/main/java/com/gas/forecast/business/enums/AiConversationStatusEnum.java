package com.gas.forecast.business.enums;

import lombok.Getter;

/** AI 会话生命周期状态。 */
@Getter
public enum AiConversationStatusEnum {
    /** 会话可继续接收消息。 */
    ACTIVE("ACTIVE"),
    /** 会话已归档，仅可查询历史记录。 */
    ARCHIVED("ARCHIVED");

    private final String code;

    AiConversationStatusEnum(String code) {
        this.code = code;
    }
}
