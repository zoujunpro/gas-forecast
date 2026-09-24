package com.gas.forecast.common.core;

public enum BusinessResponseCode implements ResponseCode {
    SUCCESS("0000", "成功"), SYSTEM_ERROR("9999", "系统繁忙，请稍后再试"), FAIL("9998", "异常"), PARAM_ERROR("9997", "参数错误"), NOT_FOUND("9996", "数据不存在"), METHOD_NOT_ALLOWED("9995", "请求方法不支持"), LOGIN_FAILED("4000",
            "用户名或密码错误"), LOGIN_ENCRYPTION_PARAM_ERROR("4001", "登录加密参数无效"), LOGIN_PAGE_EXPIRED("4002", "登录页面已过期，请刷新后重试"), LOGIN_DECRYPT_FAILED("4003", "登录参数解密失败，请刷新后重试");

    private final String code;
    private final String message;

    BusinessResponseCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
