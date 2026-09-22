package com.gas.forecast.system.service;

import com.gas.forecast.system.dto.resp.AuthCaptchaResponse;

public interface CaptchaService {
    AuthCaptchaResponse createCaptcha();

    void validate(String captchaId, String captchaCode);
}
