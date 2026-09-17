package com.gas.forecast.business.dto.resp;

import java.util.List;

public record ForecastDashboardRespDTO(
        ForecastSummaryRespDTO summary,
        List<ForecastPointRespDTO> forecastPoints,
        List<CustomerForecastPointRespDTO> customerForecastPoints,
        List<ModelRankRespDTO> modelRanks,
        List<FeatureRankRespDTO> featureRanks,
        List<BacktestDetailRespDTO> backtestDetails
) {
}
