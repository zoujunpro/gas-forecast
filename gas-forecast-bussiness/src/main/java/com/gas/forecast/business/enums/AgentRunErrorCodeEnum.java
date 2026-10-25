package com.gas.forecast.business.enums;

import lombok.Getter;

/** Agent Run 的服务端错误码。 */
@Getter
public enum AgentRunErrorCodeEnum {
    AGENT_SERVICE_ERROR("AGENT_SERVICE_ERROR");

    private final String code;

    AgentRunErrorCodeEnum(String code) { this.code = code; }
}
