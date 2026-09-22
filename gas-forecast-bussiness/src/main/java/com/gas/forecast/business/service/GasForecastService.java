package com.gas.forecast.business.service;

import com.gas.forecast.business.dto.response.ForecastDashboardResponse;
import com.gas.forecast.business.dto.response.ForecastDimensionResponse;
import com.gas.forecast.business.dto.response.ForecastSummaryResponse;
import java.util.List;

public interface GasForecastService {

    List<String> listProvinces();

    List<ForecastSummaryResponse> listSummaries();

    ForecastDimensionResponse listDimensions(String areaCode, String provinceCode);

    ForecastDashboardResponse getDashboard(String province);

    ForecastDashboardResponse getDashboardByCode(String provinceCode, String customerCode);
}
