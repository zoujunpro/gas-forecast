package com.gas.forecast.business.enums;

import lombok.Getter;

/** AI 对话识别出的业务意图。 */
@Getter
public enum AiIntentEnum {
    CHAT("CHAT"), QUERY("QUERY"), ANALYZE("ANALYZE"), FORECAST("FORECAST");

    private final String code;

    AiIntentEnum(String code) { this.code = code; }
}
