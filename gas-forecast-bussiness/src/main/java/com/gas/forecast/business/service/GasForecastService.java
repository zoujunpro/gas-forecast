package com.gas.forecast.business.service;

import com.gas.forecast.business.dto.resp.ForecastDashboardRespDTO;
import com.gas.forecast.business.dto.resp.ForecastDimensionRespDTO;
import com.gas.forecast.business.dto.resp.ForecastSummaryRespDTO;

import java.util.List;

public interface GasForecastService {

    List<String> listProvinces();

    List<ForecastSummaryRespDTO> listSummaries();

    ForecastDimensionRespDTO listDimensions(String areaCode, String provinceCode);

    ForecastDashboardRespDTO getDashboard(String province);

    ForecastDashboardRespDTO getDashboardByCode(String provinceCode, String customerCode);
}
