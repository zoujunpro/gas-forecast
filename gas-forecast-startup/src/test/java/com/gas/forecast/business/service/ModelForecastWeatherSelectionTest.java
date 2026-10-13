package com.gas.forecast.business.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gas.forecast.dao.domain.DataRegionWeatherDailyTb;
import java.time.LocalDate;
import java.util.Date;
import org.junit.jupiter.api.Test;

class ModelForecastWeatherSelectionTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);

    @Test
    void prefersActualWeatherForHistoricalPrediction() {
        assertTrue(ModelForecastManagementService.preferWeather(weather(2, "ACTUAL", null), weather(1, "FORECAST", new Date(2_000)), TODAY.minusDays(1), TODAY));
        assertFalse(ModelForecastManagementService.preferWeather(weather(2, "FORECAST", new Date(2_000)), weather(1, "ACTUAL", null), TODAY.minusDays(1), TODAY));
    }

    @Test
    void prefersForecastWeatherForFuturePrediction() {
        assertTrue(ModelForecastManagementService.preferWeather(weather(2, "FORECAST", new Date(2_000)), weather(1, "ACTUAL", null), TODAY.plusDays(1), TODAY));
        assertFalse(ModelForecastManagementService.preferWeather(weather(2, "ACTUAL", null), weather(1, "FORECAST", new Date(2_000)), TODAY.plusDays(1), TODAY));
    }

    @Test
    void prefersLatestIssueWithinSameWeatherType() {
        assertTrue(ModelForecastManagementService.preferWeather(weather(1, "FORECAST", new Date(2_000)), weather(2, "FORECAST", new Date(1_000)), TODAY.plusDays(1), TODAY));
    }

    private DataRegionWeatherDailyTb weather(long id, String type, Date issueTime) {
        DataRegionWeatherDailyTb weather = new DataRegionWeatherDailyTb();
        weather.setId(id);
        weather.setWeatherType(type);
        weather.setForecastIssueTime(issueTime);
        return weather;
    }
}
