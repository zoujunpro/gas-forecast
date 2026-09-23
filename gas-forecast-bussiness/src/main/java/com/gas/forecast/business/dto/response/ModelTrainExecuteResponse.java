package com.gas.forecast.business.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型训练执行结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelTrainExecuteResponse {
    /**
     * 训练配置编码。
     */
    private String trainCode;

    /**
     * 训练批次号。
     */
    private String trainBatchNo;

    /**
     * 模型编码。
     */
    private String modelCode;

    /**
     * 训练数据集大小。
     */
    private Integer datasetSize;

    /**
     * 状态。
     */
    private String status;

    /**
     * 请求报文。
     */
    private JsonNode requestPayload;

    /**
     * 智能体原始应答。
     */
    private JsonNode agentResponse;
}
