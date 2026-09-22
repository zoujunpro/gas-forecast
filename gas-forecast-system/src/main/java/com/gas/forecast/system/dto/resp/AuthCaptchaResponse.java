package com.gas.forecast.system.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthCaptchaResponse {
    private String captchaId;

    private String image;

    public String captchaId() {
        return captchaId;
    }

    public String image() {
        return image;
    }
}
