package com.gas.forecast.business.dto.request;

import jakarta.validation.constraints.NotNull;
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
    @NotNull(message = "训练配置ID不能为空")
    private Long trainConfigId;

    @Size(max = 64, message = "训练批次号长度不能超过64个字符")
    private String retryBatchNo;

    /** 新批次复用数据和动态特征时所参考的历史批次号。 */
    @Size(max = 64, message = "来源批次号长度不能超过64个字符")
    private String sourceBatchNo;
}
