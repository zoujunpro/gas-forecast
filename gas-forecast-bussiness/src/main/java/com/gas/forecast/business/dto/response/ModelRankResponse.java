package com.gas.forecast.business.dto.response;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 候选模型排名应答参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelRankResponse {
    /**
     * 模型名称。
     */
    private String modelName;

    /**
     * 模型类型。
     */
    private String modelType;

    /**
     * MAPE指标。
     */
    private BigDecimal mape;

    /**
     * WMAPE指标。
     */
    private BigDecimal wmape;

    /**
     * RMSE指标。
     */
    private BigDecimal rmse;

    /**
     * MAE指标。
     */
    private BigDecimal mae;

    /**
     * R方指标。
     */
    private BigDecimal r2;

    /**
     * 组成模型。
     */
    private String constituents;
}
