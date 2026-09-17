package com.gas.forecast.common.core;

public class BusinessException extends RuntimeException {

    private final String code;

    public BusinessException(String message) {
        this(BusinessResponseCode.FAIL, message);
    }

    public BusinessException(ResponseCode status) {
        this(status.getCode(), status.getMessage());
    }

    public BusinessException(ResponseCode status, String message) {
        this(status.getCode(), message);
    }

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
