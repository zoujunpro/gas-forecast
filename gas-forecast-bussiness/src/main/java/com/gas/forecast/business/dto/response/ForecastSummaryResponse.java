package com.gas.forecast.business.dto.response;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 预测摘要应答参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ForecastSummaryResponse {
    /**
     * 省份编码。
     */
    private String provinceCode;

    /**
     * 省份名称。
     */
    private String province;

    /**
     * 最优模型。
     */
    private String bestModel;

    /**
     * 回测MAPE。
     */
    private BigDecimal backtestMape;

    /**
     * 回测WMAPE。
     */
    private BigDecimal backtestWmape;

    /**
     * 回测RMSE。
     */
    private BigDecimal backtestRmse;

    /**
     * 特征数量。
     */
    private Integer featureCount;

    /**
     * 预测时域。
     */
    private Integer forecastHorizon;

    /**
     * 气象数据来源。
     */
    private String weatherSource;

    /**
     * 图表路径。
     */
    private String chartPath;
}
