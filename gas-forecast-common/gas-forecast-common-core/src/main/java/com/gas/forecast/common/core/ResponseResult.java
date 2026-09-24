package com.gas.forecast.common.core;

import java.io.Serializable;

public class ResponseResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private String code;
    private String message;
    private T data;

    public ResponseResult() {
    }

    public ResponseResult(String code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> ResponseResult<T> success(T data) {
        return of(BusinessResponseCode.SUCCESS, data);
    }

    public static <T> ResponseResult<T> of(ResponseCode status, T data) {
        return new ResponseResult<>(status.getCode(), status.getMessage(), data);
    }

    public static <T> ResponseResult<T> error(String code, String message) {
        return new ResponseResult<>(code, message, null);
    }

    public static <T> ResponseResult<T> error(ResponseCode status) {
        return new ResponseResult<>(status.getCode(), status.getMessage(), null);
    }

    public static <T> ResponseResult<T> error(ResponseCode status, String message) {
        return new ResponseResult<>(status.getCode(), message, null);
    }

    public boolean isSuccess() {
        return BusinessResponseCode.SUCCESS.getCode().equals(code);
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
