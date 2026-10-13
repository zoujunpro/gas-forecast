package com.gas.forecast.business.service;

import com.gas.forecast.business.dto.request.DailyWeatherCreateRequest;
import com.gas.forecast.business.dto.request.DailyWeatherPageRequest;
import com.gas.forecast.business.dto.request.DailyWeatherUpdateRequest;
import com.gas.forecast.business.dto.response.DailyWeatherResponse;
import com.gas.forecast.common.core.PageInfoDTO;

public interface DailyWeatherService {
    PageInfoDTO<DailyWeatherResponse> listPage(DailyWeatherPageRequest request);
    DailyWeatherResponse create(DailyWeatherCreateRequest request);
    DailyWeatherResponse update(DailyWeatherUpdateRequest request);
    void delete(Long id);
}
