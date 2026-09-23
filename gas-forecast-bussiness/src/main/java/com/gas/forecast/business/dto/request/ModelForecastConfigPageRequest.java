package com.gas.forecast.business.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 模型预测配置分页查询请求。 */
@Data
public class ModelForecastConfigPageRequest {
    @Min(1)
    private Integer page;
    @Min(1)
    private Integer size;
    @Size(max = 128)
    private String keyword;
    @Size(max = 64)
    private String agentCode;
    private Integer enabled;
}
