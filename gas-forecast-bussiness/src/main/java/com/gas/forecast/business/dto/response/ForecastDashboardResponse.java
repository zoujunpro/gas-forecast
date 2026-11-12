package com.gas.forecast.business.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 预测看板应答参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ForecastDashboardResponse {
    /**
     * 摘要信息。
     */
    private ForecastSummaryResponse summary;

    /**
     * 预测点列表。
     */
    private List<ForecastPointResponse> forecastPoints;

    /**
     * 客户预测点列表。
     */
    private List<CustomerForecastPointResponse> customerForecastPoints;

    /**
     * 模型排名列表。
     */
    private List<ModelRankResponse> modelRanks;

    /**
     * 特征排名列表。
     */
    private List<FeatureRankResponse> featureRanks;

    /**
     * 回测明细列表。
     */
    private List<BacktestDetailResponse> backtestDetails;
}
