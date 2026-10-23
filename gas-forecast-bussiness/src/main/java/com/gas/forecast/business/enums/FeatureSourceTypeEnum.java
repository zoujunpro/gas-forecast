package com.gas.forecast.business.enums;

import java.util.Arrays;

/** 特征数据来源。 */
public enum FeatureSourceTypeEnum {
    /** 内部数据库。 */
    DATABASE,

    /** 外部数据接口。 */
    API,

    /** 大模型从研报中抽取。 */
    REPORT_LLM;

    public static FeatureSourceTypeEnum fromCode(String code) {
        return Arrays.stream(values()).filter(item -> item.name().equalsIgnoreCase(code == null ? "" : code.trim())).findFirst().orElseThrow(() -> new IllegalArgumentException("不支持的特征数据来源：" + code));
    }
}
