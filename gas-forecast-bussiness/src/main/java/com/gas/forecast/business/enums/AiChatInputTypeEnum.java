package com.gas.forecast.business.enums;

/** 用户本轮聊天输入类型。 */
public enum AiChatInputTypeEnum {
    /** 普通对话输入，不承接待处理的澄清或确认。 */
    TEXT,
    /** 对当前待处理澄清问题的自然语言回复。 */
    CLARIFICATION_REPLY,
    /** 对当前待确认操作的自然语言回复，不预设确认或取消。 */
    INTERACTION_REPLY
}
