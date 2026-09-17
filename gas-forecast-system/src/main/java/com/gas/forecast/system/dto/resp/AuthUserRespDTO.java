package com.gas.forecast.system.dto.resp;

public record AuthUserRespDTO(
        Long id,
        String username,
        String realName,
        String avatar,
        String email,
        String phone,
        String orgCode
) {
}
