package com.gas.forecast.business.enums;

import lombok.Getter;

/** 澄清或确认交互的持久化状态。 */
@Getter
public enum AiInteractionStatusEnum {
    WAITING("WAITING"), ANSWERED("ANSWERED"), INVALID("INVALID"), CANCELLED("CANCELLED"), EXPIRED("EXPIRED");

    private final String code;

    AiInteractionStatusEnum(String code) {
        this.code = code;
    }
}
