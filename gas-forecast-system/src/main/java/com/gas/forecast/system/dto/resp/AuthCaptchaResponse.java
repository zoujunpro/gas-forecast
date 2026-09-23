package com.gas.forecast.system.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 验证码应答参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthCaptchaResponse {
    /**
     * 验证码ID。
     */
    private String captchaId;

    /**
     * 验证码图片Base64数据。
     */
    private String image;
}
