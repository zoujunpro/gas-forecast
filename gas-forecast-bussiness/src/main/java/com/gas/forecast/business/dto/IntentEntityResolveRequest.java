package com.gas.forecast.business.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Agent 识别意图后，按用户原始目标组合提交的实体消歧请求。 */
public record IntentEntityResolveRequest(
        @NotBlank String intent,
        String dimensionType,
        @NotEmpty @Size(max = 5000) List<@Valid Target> targets,
        @Min(1) @Max(50) Integer limit) {

    public record Target(
            @Size(max = 100) List<String> customerNames,
            @Size(max = 100) List<String> regionNames,
            @Size(max = 100) List<String> industryNames) {
    }
}
