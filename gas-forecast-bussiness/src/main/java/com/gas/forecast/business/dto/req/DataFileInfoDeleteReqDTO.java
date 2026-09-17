package com.gas.forecast.business.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * 原始数据文件删除请求参数。
 */
public record DataFileInfoDeleteReqDTO(
        /**
         * 文件记录ID。
         */
        @NotNull(message = "文件记录ID不能为空")
        Long id
) {
}
