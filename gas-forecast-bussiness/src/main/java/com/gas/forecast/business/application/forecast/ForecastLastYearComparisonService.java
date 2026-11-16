package com.gas.forecast.business.application.forecast;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gas.forecast.dao.domain.DataDailySalesTb;
import com.gas.forecast.dao.domain.DataMonthlySalesTb;
import com.gas.forecast.dao.mapper.DataMonthlySalesTbMapper;
import com.gas.forecast.dao.mapper.ForecastTendayComparisonMapper;
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

    private final DataMonthlySalesTbMapper monthlySalesMapper;
    private final ForecastTendayComparisonMapper tendayComparisonMapper;

    public List<Map<String, Object>> lastYearPoints(ModelExecuteRecordTb record) {
        // 新记录复用预测时保存的同期快照，避免查询时源数据变化导致页面与研判不一致。
        if (record != null && hasText(record.getForecastResultJson())) {
            try {
                JsonNode saved = objectMapper.readTree(record.getForecastResultJson()).path("statistics").path("lastYearPoints");
                if (saved.isArray()) return objectMapper.convertValue(saved, new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
            } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
                // 历史记录兼容原有查询路径。
            }
        }
        return lastYearPoints(record, comparisonDates(record));
    }

    public List<Map<String, Object>> lastYearPoints(ModelExecuteRecordTb record, List<String> forecastDates) {
        if (forecastDates.isEmpty())
            return List.of();
        Map<String, String> comparisonDateByForecastDate = new LinkedHashMap<>();
        for (String forecastDate : forecastDates) {
            LocalDate date = localDate(forecastDate);
            if (date != null && !(date.getMonthValue() == 2 && date.getDayOfMonth() == 29))
                comparisonDateByForecastDate.put(forecastDate, date.minusYears(1).toString());
        }
        if (comparisonDateByForecastDate.isEmpty())
            return List.of();

        Map<String, BigDecimal> salesByDate = new HashMap<>();
        if ("month".equals(record.getForecastUnit())) {
            List<String> monthKeys = comparisonDateByForecastDate.values().stream().map(date -> date.substring(0, 7)).distinct().toList();
            List<String> storedMonthDates = new ArrayList<>(monthKeys);
            monthKeys.forEach(month -> storedMonthDates.add(month + "-01"));
            var query = new LambdaQueryWrapper<DataMonthlySalesTb>().isNotNull(DataMonthlySalesTb::getGasSales)
                    .in(DataMonthlySalesTb::getStatDate, storedMonthDates);
            if (hasText(record.getCustomerCode())) query.eq(DataMonthlySalesTb::getCustomerCode, record.getCustomerCode());
            else {
                if (hasText(record.getRegionCode())) query.eq(DataMonthlySalesTb::getRegionCode, record.getRegionCode());
                if (hasText(record.getIndustryCode())) query.eq(DataMonthlySalesTb::getIndustryCode, record.getIndustryCode());
            }
            for (DataMonthlySalesTb row : monthlySalesMapper.selectList(query)) {
                BigDecimal value = decimal(row.getGasSales());
                if (value != null) salesByDate.merge(row.getStatDate().substring(0, 7), value, BigDecimal::add);
            }
        } else if ("tenday".equals(record.getForecastUnit())) {
            // 旬数据仅有地区维度，不能冒充客户或行业口径。
            if (hasText(record.getCustomerCode()) || hasText(record.getIndustryCode()) || !hasText(record.getRegionCode())) return List.of();
            for (Map<String, Object> row : tendayComparisonMapper.selectComparison(record.getRegionCode(), new ArrayList<>(comparisonDateByForecastDate.values()))) {
                BigDecimal value = decimal(String.valueOf(row.get("value")));
                if (value != null) salesByDate.merge(String.valueOf(row.get("date")), value, BigDecimal::add);
            }
        } else {
            LambdaQueryWrapper<DataDailySalesTb> query = new LambdaQueryWrapper<DataDailySalesTb>().isNotNull(DataDailySalesTb::getGasSales).in(DataDailySalesTb::getStatDate, comparisonDateByForecastDate.values());
            if (hasText(record.getCustomerCode()))
                query.eq(DataDailySalesTb::getCustomerCode, record.getCustomerCode());
            else {
                if (hasText(record.getRegionCode()))
                    query.eq(DataDailySalesTb::getRegionCode, record.getRegionCode());
                if (hasText(record.getIndustryCode()))
                    query.eq(DataDailySalesTb::getIndustryCode, record.getIndustryCode());
            }
            for (DataDailySalesTb sales : dataDailySalesTbMapper.selectList(query)) {
                BigDecimal value = decimal(sales.getGasSales());
                if (value != null)
                    salesByDate.merge(sales.getStatDate(), value, BigDecimal::add);
            }
        }
        List<Map<String, Object>> points = new ArrayList<>();
        for (Map.Entry<String, String> entry : comparisonDateByForecastDate.entrySet()) {
            BigDecimal value = salesByDate.get("month".equals(record.getForecastUnit()) ? entry.getValue().substring(0, 7) : entry.getValue());
            if (value != null)
                points.add(Map.of("date", entry.getKey(), "value", value));
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
        if (!hasText(resultJson))
            return;
        try {
            JsonNode root = objectMapper.readTree(resultJson);
            JsonNode points = root.path("points");
            if (!points.isArray())
                points = root.path("rolling_backtest_results");
            if (!points.isArray())
                return;
            for (JsonNode point : points) {
                String date = firstText(point, "date", "forecast_date", "forecastDate", "stat_date", "statDate");
                if (hasText(date))
                    dates.add(date);
            }
        } catch (Exception ignored) {
        }
    }

    private String firstText(JsonNode node, String... names) {
        for (String name : names) {
            String value = node.path(name).asText("");
            if (hasText(value))
                return value;
        }
        return "";
    }

    private LocalDate localDate(String value) {
        try {
            return LocalDate.parse(value.length() >= 10 ? value.substring(0, 10) : value);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private BigDecimal decimal(String value) {
        try {
            return !hasText(value) ? null : new BigDecimal(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
