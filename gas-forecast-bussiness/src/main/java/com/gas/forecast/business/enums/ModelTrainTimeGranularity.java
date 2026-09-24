package com.gas.forecast.business.enums;

import java.util.Arrays;

/** 模型训练数据时间粒度。 */
public enum ModelTrainTimeGranularity {
    /** 日。 */
    DAY,

    /** 旬。 */
    TENDAY,

    /** 月。 */
    MONTH;

    public static ModelTrainTimeGranularity fromCode(String code) {
        return Arrays.stream(values()).filter(item -> item.name().equalsIgnoreCase(code == null ? "" : code.trim())).findFirst().orElseThrow(() -> new IllegalArgumentException("不支持的训练时间粒度：" + code));
    }
}
