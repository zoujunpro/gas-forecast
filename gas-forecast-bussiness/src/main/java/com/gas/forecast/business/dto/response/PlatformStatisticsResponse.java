package com.gas.forecast.business.dto.response;

public record PlatformStatisticsResponse(Long regions, Long industries, Long customers, Long history, Long models, String latestUpdate) {
}
