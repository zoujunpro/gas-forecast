package com.gas.forecast.business.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 原始数据文件删除请求参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataFileInfoDeleteRequest {
    /**
     * 文件记录ID。
     */
    @NotNull(message = "文件记录ID不能为空")
    private Long id;

    public Long id() {
        return id;
    }
}
