package com.gas.forecast.business.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ForecastDashboardResponse {
    private ForecastSummaryResponse summary;

    private List<ForecastPointResponse> forecastPoints;

    private List<CustomerForecastPointResponse> customerForecastPoints;

    private List<ModelRankResponse> modelRanks;

    private List<FeatureRankResponse> featureRanks;

    private List<BacktestDetailResponse> backtestDetails;

    public ForecastSummaryResponse summary() {
        return summary;
    }

    public List<ForecastPointResponse> forecastPoints() {
        return forecastPoints;
    }

    public List<CustomerForecastPointResponse> customerForecastPoints() {
        return customerForecastPoints;
    }

    public List<ModelRankResponse> modelRanks() {
        return modelRanks;
    }

    public List<FeatureRankResponse> featureRanks() {
        return featureRanks;
    }

    public List<BacktestDetailResponse> backtestDetails() {
        return backtestDetails;
    }
}
