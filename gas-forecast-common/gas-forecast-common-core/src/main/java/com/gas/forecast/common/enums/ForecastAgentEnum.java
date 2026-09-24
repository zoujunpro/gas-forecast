package com.gas.forecast.common.enums;

import java.util.Arrays;
import java.util.Optional;

/**
 * 预测智能体枚举。
 */
public enum ForecastAgentEnum {
    WINTER_SUPPLY("winter-supply", "WINTER_SUPPLY", "冬季保供预测智能体", "MULTI_SELECT"), MONTHLY_SALES("monthly-sales", "MONTHLY_SALES", "月度销量预测智能体", "SINGLE"), SHORT_CUSTOMER("short-term", "SHORT_CUSTOMER",
            "短期客户预测智能体", "SINGLE");

    private final String agentCode;
    private final String sceneCode;
    private final String agentName;
    private final String strategyType;

    ForecastAgentEnum(String agentCode, String sceneCode, String agentName, String strategyType) {
        this.agentCode = agentCode;
        this.sceneCode = sceneCode;
        this.agentName = agentName;
        this.strategyType = strategyType;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public String getSceneCode() {
        return sceneCode;
    }

    public String getAgentName() {
        return agentName;
    }

    public String getStrategyType() {
        return strategyType;
    }

    public static Optional<ForecastAgentEnum> ofAgentCode(String agentCode) {
        return Arrays.stream(values()).filter(item -> item.agentCode.equals(agentCode)).findFirst();
    }

    public static Optional<ForecastAgentEnum> ofSceneCode(String sceneCode) {
        return Arrays.stream(values()).filter(item -> item.sceneCode.equals(sceneCode)).findFirst();
    }

    public static boolean isValidAgentCode(String agentCode) {
        return ofAgentCode(agentCode).isPresent();
    }

    public static boolean isValidSceneCode(String sceneCode) {
        return ofSceneCode(sceneCode).isPresent();
    }
}
