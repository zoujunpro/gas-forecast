package com.gas.forecast.system.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthUserResponse {
    private Long id;

    private String username;

    private String realName;

    private String avatar;

    private String email;

    private String phone;

    private String orgCode;

    public Long id() {
        return id;
    }

    public String username() {
        return username;
    }

    public String realName() {
        return realName;
    }

    public String avatar() {
        return avatar;
    }

    public String email() {
        return email;
    }

    public String phone() {
        return phone;
    }

    public String orgCode() {
        return orgCode;
    }
}
