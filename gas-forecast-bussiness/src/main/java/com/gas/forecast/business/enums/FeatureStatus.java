package com.gas.forecast.business.enums;

import java.util.Arrays;

/** 特征生命周期状态。 */
public enum FeatureStatus {
    /** 已发现，等待审核或验证。 */
    CANDIDATE,

    /** 已生效，可以参与模型配置。 */
    ACTIVE,

    /** 已停用，不再用于新的模型配置。 */
    DISABLED;

    public static FeatureStatus fromCode(String code) {
        return Arrays.stream(values()).filter(item -> item.name().equalsIgnoreCase(code == null ? "" : code.trim())).findFirst().orElseThrow(() -> new IllegalArgumentException("不支持的特征状态：" + code));
    }
}
