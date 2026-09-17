package com.gas.forecast.system.service;

import com.gas.forecast.system.dto.resp.AuthCaptchaRespDTO;

public interface CaptchaService {
    AuthCaptchaRespDTO createCaptcha();

    void validate(String captchaId, String captchaCode);
}
