package com.gas.forecast.business.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 模型预测配置更新请求。 */
@Data
public class ModelForecastConfigUpdateRequest {
    @NotNull(message = "预测配置ID不能为空")
    private Long id;
    @NotBlank(message = "预测名称不能为空")
    @Size(max = 128)
    private String forecastName;
    @NotBlank(message = "预测开始日期不能为空")
    private String forecastStartDate;
    @Min(1)
    @Max(366)
    private Integer forecastHorizon;
    private String forecastFrequency;
    @NotBlank(message = "请选择模型训练配置")
    private String trainConfigCode;
    private Integer enabled;
    @Size(max = 512)
    private String remark;
}
