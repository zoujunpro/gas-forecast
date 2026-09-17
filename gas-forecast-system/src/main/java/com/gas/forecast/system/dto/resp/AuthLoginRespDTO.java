package com.gas.forecast.system.dto.resp;

import java.util.List;

public record AuthLoginRespDTO(
        String token,
        AuthUserRespDTO user,
        List<String> roles,
        List<String> permissions,
        List<AuthMenuRespDTO> menus
) {
}
