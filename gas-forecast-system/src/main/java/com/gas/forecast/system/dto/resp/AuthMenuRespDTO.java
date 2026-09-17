package com.gas.forecast.system.dto.resp;

import java.util.List;

public record AuthMenuRespDTO(
        Long id,
        Long parentId,
        String name,
        String path,
        String component,
        String icon,
        Integer sortNo,
        Integer hidden,
        List<AuthMenuRespDTO> children
) {
}
