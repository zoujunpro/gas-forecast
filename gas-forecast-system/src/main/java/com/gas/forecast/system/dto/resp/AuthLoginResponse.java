package com.gas.forecast.system.dto.resp;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthLoginResponse {
    private String token;

    private AuthUserResponse user;

    private List<String> roles;

    private List<String> permissions;

    private List<AuthMenuResponse> menus;

    public String token() {
        return token;
    }

    public AuthUserResponse user() {
        return user;
    }

    public List<String> roles() {
        return roles;
    }

    public List<String> permissions() {
        return permissions;
    }

    public List<AuthMenuResponse> menus() {
        return menus;
    }
}
