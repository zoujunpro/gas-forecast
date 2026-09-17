package com.gas.forecast.business.enums;

public enum BaseCodeType {
    REGION("REG", 6),
    INDUSTRY("IND", 6),
    CUSTOMER("CUS", 6),
    FILE("FIL", 6),
    MODEL_CONFIG("MCF", 6);

    private final String prefix;
    private final int width;

    BaseCodeType(String prefix, int width) {
        this.prefix = prefix;
        this.width = width;
    }

    public String prefix() {
        return prefix;
    }

    public int width() {
        return width;
    }
}
