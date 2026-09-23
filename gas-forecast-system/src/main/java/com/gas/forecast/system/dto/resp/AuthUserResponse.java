package com.gas.forecast.system.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前登录用户应答参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthUserResponse {
    /**
     * 用户ID。
     */
    private Long id;

    /**
     * 用户名。
     */
    private String username;

    /**
     * 用户姓名。
     */
    private String realName;

    /**
     * 头像地址。
     */
    private String avatar;

    /**
     * 邮箱地址。
     */
    private String email;

    /**
     * 手机号码。
     */
    private String phone;

    /**
     * 组织编码。
     */
    private String orgCode;
}
