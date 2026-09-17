package com.gas.forecast.system.dto.resp;

public record AuthCaptchaRespDTO(
        String captchaId,
        String image
) {
}
