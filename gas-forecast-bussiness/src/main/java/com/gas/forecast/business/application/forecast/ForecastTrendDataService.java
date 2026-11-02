package com.gas.forecast.business.application.forecast;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.dao.domain.DataDailySalesTb;
import com.gas.forecast.dao.domain.DataRegionWeatherDailyTb;
import com.gas.forecast.dao.domain.ModelExecuteRecordTb;
import com.gas.forecast.dao.domain.ModelForecastResultTb;
import com.gas.forecast.dao.mapper.DataDailySalesTbMapper;
import com.gas.forecast.dao.mapper.DataRegionWeatherDailyTbMapper;
import com.gas.forecast.dao.mapper.ModelForecastResultTbMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 趋势图的唯一数据源：预测表负责未来值，日销量表负责历史及去年同期。 */
@Service
@RequiredArgsConstructor
public class ForecastTrendDataService {
    private final ModelForecastResultTbMapper modelForecastResultTbMapper;
    private final DataDailySalesTbMapper dataDailySalesTbMapper;
    private final DataRegionWeatherDailyTbMapper dataRegionWeatherDailyTbMapper;

    public Map<String, Object> trend(ModelExecuteRecordTb record) {
        if (record == null || record.getForecastNo() == null) return Map.of();
        List<ModelForecastResultTb> forecasts = modelForecastResultTbMapper.selectList(Wrappers.<ModelForecastResultTb>lambdaQuery()
                .eq(ModelForecastResultTb::getForecastNo, record.getForecastNo())
                .orderByAsc(ModelForecastResultTb::getForecastDate));
        if (forecasts.isEmpty()) return Map.of("forecastPoints", List.of(), "historyPoints", List.of(), "lastYearPoints", List.of());
        List<Map<String, Object>> forecastPoints = forecasts.stream().map(point -> point(point.getForecastDate(), point.getForecastValue())).toList();
        LocalDate forecastStart = LocalDate.parse(forecasts.getFirst().getForecastDate().substring(0, 10));
        LocalDate forecastEnd = LocalDate.parse(forecasts.getLast().getForecastDate().substring(0, 10));
        int days = forecasts.size();
        // 历史段必须以实际可用历史数据的结束日为准，而不是机械地从预测起点倒推。
        // 预测日期落在未来时，后者会产生尚未发生的“历史窗口”，导致去年同期线从预测区间才开始。
        LocalDate historyEnd = record.getHistoryEndDate() == null
                ? forecastStart.minusDays(1)
                : record.getHistoryEndDate();
        if (historyEnd.isAfter(forecastStart.minusDays(1))) historyEnd = forecastStart.minusDays(1);
        LocalDate historyStart = historyEnd.minusDays(Math.max(0, days - 1));
        List<Map<String, Object>> historyPoints = sales(record, historyStart, historyEnd);
        List<Map<String, Object>> forecastActualPoints = sales(record, forecastStart, forecastEnd);
        // 去年同期需要分别映射到“历史段”和“预测段”的显示日期；这样灰线的起点必然与绿色历史线对齐。
        List<Map<String, Object>> lastYearHistory = shift(sales(record, historyStart.minusYears(1), historyEnd.minusYears(1)));
        List<Map<String, Object>> lastYearForecast = shift(sales(record, forecastStart.minusYears(1), forecastEnd.minusYears(1)));
        List<Map<String, Object>> lastYearPoints = new ArrayList<>(lastYearHistory.size() + lastYearForecast.size());
        lastYearPoints.addAll(lastYearHistory);
        lastYearPoints.addAll(lastYearForecast);
        return Map.of("forecastPoints", forecastPoints, "historyPoints", historyPoints, "forecastActualPoints", forecastActualPoints, "lastYearPoints", lastYearPoints);
    }

    /**
     * 返回本次实际送入模型的历史训练样本。字段与 ForecastExecutionPayloadFactory 的
     * history_dataset 保持一致，避免页面把预测结果误展示为训练数据。
     */
    public Map<String, Object> trainingData(ModelExecuteRecordTb record) {
        if (record == null || record.getHistoryStartDate() == null || record.getHistoryEndDate() == null) {
            return Map.of("rows", List.of(), "weatherFeatureNames", List.of());
        }
        var query = Wrappers.<DataDailySalesTb>lambdaQuery()
                .isNotNull(DataDailySalesTb::getGasSales)
                .ge(DataDailySalesTb::getStatDate, record.getHistoryStartDate().toString())
                .le(DataDailySalesTb::getStatDate, record.getHistoryEndDate().toString());
        applyScope(query, record);
        List<DataDailySalesTb> sales = dataDailySalesTbMapper.selectList(query
                .orderByAsc(DataDailySalesTb::getStatDate)
                .orderByAsc(DataDailySalesTb::getId));
        Map<String, DataRegionWeatherDailyTb> weatherByDate = weatherByDate(record);
        List<Map<String, Object>> rows = sales.stream().map(item -> trainingRow(item, weatherByDate.get(item.getStatDate()))).toList();
        return Map.of(
                "rows", rows,
                "weatherFeatureNames", List.of("max_temp", "min_temp", "avg_temp", "humidity", "precipitation", "wind_speed", "hdd"),
                "historyStartDate", record.getHistoryStartDate().toString(),
                "historyEndDate", record.getHistoryEndDate().toString(),
                "historyRows", rows.size());
    }

    private List<Map<String, Object>> sales(ModelExecuteRecordTb record, LocalDate start, LocalDate end) {
        var query = Wrappers.<DataDailySalesTb>lambdaQuery().isNotNull(DataDailySalesTb::getGasSales)
                .ge(DataDailySalesTb::getStatDate, start.toString()).le(DataDailySalesTb::getStatDate, end.toString());
        applyScope(query, record);
        Map<String, BigDecimal> byDate = new LinkedHashMap<>();
        for (DataDailySalesTb item : dataDailySalesTbMapper.selectList(query)) byDate.merge(item.getStatDate(), new BigDecimal(item.getGasSales()), BigDecimal::add);
        return byDate.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> point(entry.getKey(), entry.getValue())).toList();
    }

    private List<Map<String, Object>> shift(List<Map<String, Object>> points) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> point : points) result.add(point(LocalDate.parse(String.valueOf(point.get("date"))).plusYears(1).toString(), point.get("value")));
        return result;
    }
    private Map<String, Object> point(String date, Object value) { return Map.of("date", date, "value", value); }

    private void applyScope(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DataDailySalesTb> query, ModelExecuteRecordTb record) {
        if (record.getCustomerCode() != null && !record.getCustomerCode().isBlank()) query.eq(DataDailySalesTb::getCustomerCode, record.getCustomerCode());
        else {
            if (record.getRegionCode() != null && !record.getRegionCode().isBlank()) query.eq(DataDailySalesTb::getRegionCode, record.getRegionCode());
            if (record.getIndustryCode() != null && !record.getIndustryCode().isBlank()) query.eq(DataDailySalesTb::getIndustryCode, record.getIndustryCode());
        }
    }

    private Map<String, DataRegionWeatherDailyTb> weatherByDate(ModelExecuteRecordTb record) {
        if (record.getRegionCode() == null || record.getRegionCode().isBlank()) return Map.of();
        var query = Wrappers.<DataRegionWeatherDailyTb>lambdaQuery()
                .eq(DataRegionWeatherDailyTb::getRegionCode, record.getRegionCode())
                .eq(DataRegionWeatherDailyTb::getWeatherType, "ACTUAL")
                .ge(DataRegionWeatherDailyTb::getStatDate, record.getHistoryStartDate())
                .le(DataRegionWeatherDailyTb::getStatDate, record.getHistoryEndDate());
        Map<String, DataRegionWeatherDailyTb> result = new HashMap<>();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        for (DataRegionWeatherDailyTb weather : dataRegionWeatherDailyTbMapper.selectList(query
                .orderByDesc(DataRegionWeatherDailyTb::getUpdatedAt)
                .orderByDesc(DataRegionWeatherDailyTb::getId))) {
            if (weather.getStatDate() != null) result.putIfAbsent(formatter.format(weather.getStatDate()), weather);
        }
        return result;
    }

    private Map<String, Object> trainingRow(DataDailySalesTb sale, DataRegionWeatherDailyTb weather) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("date", sale.getStatDate());
        row.put("gasSales", sale.getGasSales());
        if (weather != null) {
            row.put("tempMax", weather.getTempMax());
            row.put("tempMin", weather.getTempMin());
            row.put("tempAvg", weather.getTempAvg());
            row.put("humidity", weather.getHumidity());
            row.put("precipitation", weather.getPrecipitation());
            row.put("windSpeed", weather.getWindSpeed());
            row.put("hdd", weather.getHdd());
        }
        return row;
    }
}
