package com.gas.forecast.business.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型训练执行请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelTrainExecuteRequest {
    @Size(max = 64, message = "训练配置编码长度不能超过64个字符")
    private String trainCode;

    @Size(max = 64, message = "训练配置编码长度不能超过64个字符")
    private String configCode;

    @Size(max = 64, message = "训练批次号长度不能超过64个字符")
    private String retryBatchNo;

    public String trainCode() {
        return trainCode;
    }

    public String configCode() {
        return configCode;
    }

    public String retryBatchNo() {
        return retryBatchNo;
    }
}
