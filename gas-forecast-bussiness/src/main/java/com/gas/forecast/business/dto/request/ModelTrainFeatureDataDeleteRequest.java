package com.gas.forecast.business.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 训练特征数据删除请求参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelTrainFeatureDataDeleteRequest {
    @NotNull(message = "训练特征数据ID不能为空")
    private Long id;

    public Long id() {
        return id;
    }
}
