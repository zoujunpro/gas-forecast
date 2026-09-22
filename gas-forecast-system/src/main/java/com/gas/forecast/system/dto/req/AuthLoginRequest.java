package com.gas.forecast.system.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthLoginRequest {
    @NotBlank(message = "用户名不能为空")
    private @Size(max = 512, message = "用户名长度不能超过512个字符") String username;

    @NotBlank(message = "密码不能为空")
    private @Size(max = 512, message = "密码长度不能超过512个字符") String password;

    @NotBlank(message = "验证码标识不能为空")
    private String captchaId;

    @NotBlank(message = "验证码不能为空")
    private @Size(max = 8, message = "验证码长度不能超过8个字符") String captchaCode;

    @Size(max = 1024, message = "RSA公钥长度不能超过1024个字符")
    private String rsaPublicKey;

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    public String captchaId() {
        return captchaId;
    }

    public String captchaCode() {
        return captchaCode;
    }

    public String rsaPublicKey() {
        return rsaPublicKey;
    }
}
