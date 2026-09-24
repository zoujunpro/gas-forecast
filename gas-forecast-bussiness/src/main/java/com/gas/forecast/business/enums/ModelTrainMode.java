package com.gas.forecast.business.enums;

import java.util.Arrays;

/** 模型训练数据选取方式。 */
public enum ModelTrainMode {
    /** 使用最近指定期数的数据。 */
    RECENT,

    /** 使用指定开始、结束日期范围内的数据。 */
    RANGE;

    public static ModelTrainMode fromCode(String code) {
        return Arrays.stream(values()).filter(item -> item.name().equalsIgnoreCase(code == null ? "" : code.trim())).findFirst().orElseThrow(() -> new IllegalArgumentException("不支持的训练方式：" + code));
    }
}
