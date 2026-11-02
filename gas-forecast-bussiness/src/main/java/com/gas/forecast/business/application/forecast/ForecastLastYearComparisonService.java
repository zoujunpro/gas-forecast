package com.gas.forecast.business.application.forecast;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gas.forecast.dao.domain.DataDailySalesTb;
import com.gas.forecast.dao.domain.ModelExecuteRecordTb;
import com.gas.forecast.dao.mapper.DataDailySalesTbMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 为历史及预测结果统一补充去年同期实际销量，供任务结果与预测记录页面共同使用。 */
@Service
@RequiredArgsConstructor
public class ForecastLastYearComparisonService {
    private final ObjectMapper objectMapper;
    private final DataDailySalesTbMapper dataDailySalesTbMapper;

    public List<Map<String, Object>> lastYearPoints(ModelExecuteRecordTb record) {
        List<String> forecastDates = comparisonDates(record);
        if (forecastDates.isEmpty()) return List.of();
        Map<String, String> comparisonDateByForecastDate = new LinkedHashMap<>();
        for (String forecastDate : forecastDates) {
            LocalDate date = localDate(forecastDate);
            if (date != null) comparisonDateByForecastDate.put(forecastDate, date.minusYears(1).toString());
        }
        if (comparisonDateByForecastDate.isEmpty()) return List.of();

        LambdaQueryWrapper<DataDailySalesTb> query = new LambdaQueryWrapper<DataDailySalesTb>()
                .isNotNull(DataDailySalesTb::getGasSales)
                .in(DataDailySalesTb::getStatDate, comparisonDateByForecastDate.values());
        if (hasText(record.getCustomerCode())) query.eq(DataDailySalesTb::getCustomerCode, record.getCustomerCode());
        else {
            if (hasText(record.getRegionCode())) query.eq(DataDailySalesTb::getRegionCode, record.getRegionCode());
            if (hasText(record.getIndustryCode())) query.eq(DataDailySalesTb::getIndustryCode, record.getIndustryCode());
        }
        Map<String, BigDecimal> salesByDate = new HashMap<>();
        for (DataDailySalesTb sales : dataDailySalesTbMapper.selectList(query)) {
            BigDecimal value = decimal(sales.getGasSales());
            if (value != null) salesByDate.merge(sales.getStatDate(), value, BigDecimal::add);
        }
        List<Map<String, Object>> points = new ArrayList<>();
        for (Map.Entry<String, String> entry : comparisonDateByForecastDate.entrySet()) {
            BigDecimal value = salesByDate.get(entry.getValue());
            if (value != null) points.add(Map.of("date", entry.getKey(), "value", value));
        }
        return points;
    }

    private List<String> comparisonDates(ModelExecuteRecordTb record) {
        LinkedHashSet<String> dates = new LinkedHashSet<>();
        collectDates(record == null ? null : record.getBacktestResultJson(), dates);
        collectDates(record == null ? null : record.getForecastResultJson(), dates);
        return new ArrayList<>(dates);
    }

    private void collectDates(String resultJson, LinkedHashSet<String> dates) {
        if (!hasText(resultJson)) return;
        try {
            JsonNode root = objectMapper.readTree(resultJson);
            JsonNode points = root.path("points");
            if (!points.isArray()) points = root.path("rolling_backtest_results");
            if (!points.isArray()) return;
            for (JsonNode point : points) {
                String date = firstText(point, "date", "forecast_date", "forecastDate", "stat_date", "statDate");
                if (hasText(date)) dates.add(date);
            }
        } catch (Exception ignored) { }
    }

    private String firstText(JsonNode node, String... names) {
        for (String name : names) {
            String value = node.path(name).asText("");
            if (hasText(value)) return value;
        }
        return "";
    }

    private LocalDate localDate(String value) {
        try { return LocalDate.parse(value.length() >= 10 ? value.substring(0, 10) : value); }
        catch (DateTimeParseException exception) { return null; }
    }

    private BigDecimal decimal(String value) {
        try { return !hasText(value) ? null : new BigDecimal(value); }
        catch (NumberFormatException exception) { return null; }
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }
}
