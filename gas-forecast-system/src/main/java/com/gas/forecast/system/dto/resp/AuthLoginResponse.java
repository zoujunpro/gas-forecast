package com.gas.forecast.system.dto.resp;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录及权限信息应答参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthLoginResponse {
    /**
     * 访问令牌。
     */
    private String token;

    /**
     * 当前用户信息。
     */
    private AuthUserResponse user;

    /**
     * 角色标识列表。
     */
    private List<String> roles;

    /**
     * 权限码列表。
     */
    private List<String> permissions;

    /**
     * 授权菜单树。
     */
    private List<AuthMenuResponse> menus;
}
