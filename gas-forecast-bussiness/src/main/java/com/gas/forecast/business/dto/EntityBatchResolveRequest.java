package com.gas.forecast.business.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Python Agent 内部调用的批量实体校验请求，不面向前端业务接口。 */
public record EntityBatchResolveRequest(
        @Size(max = 100) List<String> customerNames,
        @Size(max = 100) List<String> regionNames,
        @Size(max = 100) List<String> industryNames,
        @Min(1) @Max(50) Integer limit) {
}
