package com.gas.forecast.business.enums;

import lombok.Getter;

/** 模型预测任务状态。 */
@Getter
public enum ModelForecastStatus {
    RUNNING(1), SUCCESS(2), FAILED(3);

    private final int code;

    ModelForecastStatus(int code) {
        this.code = code;
    }
}
