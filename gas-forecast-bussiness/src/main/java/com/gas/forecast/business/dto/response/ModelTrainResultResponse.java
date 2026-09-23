package com.gas.forecast.business.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 模型训练批次查询结果。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelTrainResultResponse {

    /**
     * 训练批次列表；查询指定批次时只返回该批次。
     */
    private List<ModelTrainRecordResponse> batches;

    /**
     * 页面默认选中的训练批次号，取当前结果列表中的第一条记录。
     */
    private String selectedBatchNo;
}
