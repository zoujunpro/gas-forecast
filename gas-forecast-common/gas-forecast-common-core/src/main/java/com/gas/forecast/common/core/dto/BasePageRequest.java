package com.gas.forecast.common.core.dto;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BasePageRequest {
    /**
     * 当前页码，从1开始。
     */
    @Min(value = 1, message = "页码不能小于1")
    private Integer page = 1;

    /**
     * 每页条数。
     */
    @Min(value = 1, message = "每页条数不能小于1")
    private Integer size = 20;
}
