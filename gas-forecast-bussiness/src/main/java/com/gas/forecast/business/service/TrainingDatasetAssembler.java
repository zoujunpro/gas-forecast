package com.gas.forecast.business.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.DataDailySalesTb;
import com.gas.forecast.dao.domain.DataRegionWeatherDailyTb;
import com.gas.forecast.dao.domain.ModelConfigScopeTb;
import com.gas.forecast.dao.domain.ModelConfigTb;
import com.gas.forecast.dao.domain.ModelTrainConfigTb;
import com.gas.forecast.dao.domain.ModelTrainFeatureDataTb;
import com.gas.forecast.dao.mapper.DataDailySalesTbMapper;
import com.gas.forecast.dao.mapper.DataRegionWeatherDailyTbMapper;
import com.gas.forecast.dao.mapper.ModelConfigScopeTbMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 从日销量和日级天气实时组装模型所需粒度的数据。 */
@Service
@RequiredArgsConstructor
public class TrainingDatasetAssembler {
    private final DataDailySalesTbMapper salesMapper;
    private final DataRegionWeatherDailyTbMapper weatherMapper;
    private final ModelConfigScopeTbMapper scopeMapper;

    public List<ModelTrainFeatureDataTb> assemble(ModelTrainConfigTb config, ModelConfigTb model) {
        String granularity = normalizedGranularity(model.getTimeGranularity());
        String trainingScopeType = trainingScope(config);
        Scope scope = scope(config, model.getId(), trainingScopeType);
        LocalDate[] range = range(config, granularity, scope);
        if (range[0].isAfter(range[1]))
            return List.of();
        List<DataDailySalesTb> sales = salesMapper.selectList(salesQuery(scope).ge(DataDailySalesTb::getStatDate, range[0].toString()).le(DataDailySalesTb::getStatDate, range[1].toString())
                .orderByAsc(DataDailySalesTb::getStatDate).orderByAsc(DataDailySalesTb::getId));
        if (sales.isEmpty())
            return List.of();
        List<String> regions = sales.stream().map(DataDailySalesTb::getRegionCode).filter(TextUtils::hasText).distinct().toList();
        Map<String, DataRegionWeatherDailyTb> weather = weather(regions, range[0], range[1]);
        List<ModelTrainFeatureDataTb> rows = aggregate(sales, weather, granularity, trainingScopeType);
        if ("RECENT".equalsIgnoreCase(config.getTrainMode()) && config.getRecentPeriods() != null) {
            Set<String> latest = new LinkedHashSet<>(rows.stream().map(ModelTrainFeatureDataTb::getStatDate).distinct().sorted(Comparator.reverseOrder()).limit(config.getRecentPeriods()).toList());
            rows = rows.stream().filter(row -> latest.contains(row.getStatDate())).toList();
        }
        return rows;
    }

    private Scope scope(ModelTrainConfigTb config, Long modelId, String trainingScopeType) {
        Set<String> regions = set(config.getRegionCode());
        Set<String> industries = set(config.getIndustryCode());
        Set<String> customers = set(config.getCustomerCode());
        if (regions.isEmpty() && industries.isEmpty() && customers.isEmpty()) {
            for (ModelConfigScopeTb item : scopeMapper.selectList(Wrappers.<ModelConfigScopeTb>lambdaQuery().eq(ModelConfigScopeTb::getModelId, modelId))) {
                add(regions, item.getRegionCode());
                add(industries, item.getIndustryCode());
                add(customers, item.getCustomerCode());
            }
        }
        if ("REGION".equals(trainingScopeType)) {
            industries.clear();
            customers.clear();
        } else if ("REGION_INDUSTRY".equals(trainingScopeType)) {
            customers.clear();
        }
        return new Scope(regions, industries, customers);
    }

    private LocalDate[] range(ModelTrainConfigTb config, String granularity, Scope scope) {
        if (!"RECENT".equalsIgnoreCase(config.getTrainMode())) {
            return new LocalDate[]{date(config.getTrainStartDate()), date(config.getTrainEndDate())};
        }
        DataDailySalesTb latest = salesMapper.selectOne(salesQuery(scope).orderByDesc(DataDailySalesTb::getStatDate).orderByDesc(DataDailySalesTb::getId).last("limit 1"));
        if (latest == null)
            return new LocalDate[]{LocalDate.now(), LocalDate.now().minusDays(1)};
        LocalDate end = date(latest.getStatDate());
        int periods = Math.max(config.getRecentPeriods() == null ? 1 : config.getRecentPeriods(), 1);
        LocalDate start = switch (granularity) {
            case "MONTH" -> end.withDayOfMonth(1).minusMonths(periods - 1L);
            case "TENDAY" -> end.minusDays(periods * 10L + 10L);
            default -> end.minusDays(periods - 1L);
        };
        return new LocalDate[]{start, end};
    }

    private LambdaQueryWrapper<DataDailySalesTb> salesQuery(Scope scope) {
        LambdaQueryWrapper<DataDailySalesTb> query = Wrappers.lambdaQuery();
        if (!scope.regions.isEmpty())
            query.in(DataDailySalesTb::getRegionCode, scope.regions);
        if (!scope.industries.isEmpty())
            query.in(DataDailySalesTb::getIndustryCode, scope.industries);
        if (!scope.customers.isEmpty())
            query.in(DataDailySalesTb::getCustomerCode, scope.customers);
        return query;
    }

    private Map<String, DataRegionWeatherDailyTb> weather(List<String> regions, LocalDate start, LocalDate end) {
        if (regions.isEmpty())
            return Map.of();
        List<DataRegionWeatherDailyTb> values = weatherMapper
                .selectList(Wrappers.<DataRegionWeatherDailyTb>lambdaQuery().in(DataRegionWeatherDailyTb::getRegionCode, regions).eq(DataRegionWeatherDailyTb::getWeatherType, "ACTUAL")
                        .ge(DataRegionWeatherDailyTb::getStatDate, start).le(DataRegionWeatherDailyTb::getStatDate, end).orderByAsc(DataRegionWeatherDailyTb::getId));
        Map<String, DataRegionWeatherDailyTb> result = new LinkedHashMap<>();
        values.forEach(item -> result.put(key(item.getRegionCode(), localDate(item.getStatDate())), item));
        return result;
    }

    private List<ModelTrainFeatureDataTb> aggregate(List<DataDailySalesTb> sales, Map<String, DataRegionWeatherDailyTb> weather, String granularity, String trainingScopeType) {
        Map<String, SalesAggregate> salesGroups = new LinkedHashMap<>();
        for (DataDailySalesTb sale : sales) {
            LocalDate bucket = bucket(date(sale.getStatDate()), granularity);
            String dimension = switch (trainingScopeType) {
                case "CUSTOMER" -> safe(sale.getRegionCode()) + '|' + safe(sale.getIndustryCode()) + '|' + safe(sale.getCustomerCode());
                case "REGION_INDUSTRY" -> safe(sale.getRegionCode()) + '|' + safe(sale.getIndustryCode());
                default -> safe(sale.getRegionCode());
            };
            String key = dimension + '|' + bucket;
            SalesAggregate group = salesGroups.computeIfAbsent(key, ignored -> new SalesAggregate(sale, bucket));
            group.value = group.value.add(decimal(sale.getGasSales()));
        }
        Map<String, WeatherAggregate> weatherGroups = new LinkedHashMap<>();
        weather.values().forEach(item -> weatherGroups.computeIfAbsent(key(item.getRegionCode(), bucket(localDate(item.getStatDate()), granularity)), ignored -> new WeatherAggregate()).add(item));
        List<ModelTrainFeatureDataTb> result = new ArrayList<>();
        long id = 1;
        for (SalesAggregate group : salesGroups.values()) {
            ModelTrainFeatureDataTb row = row(group.sample, group.bucket.toString(), granularity, id++);
            row.setGasSales(group.value);
            if ("REGION".equals(trainingScopeType)) {
                row.setIndustryCode(null);
                row.setIndustryName(null);
                row.setCustomerCode(null);
                row.setCustomerName(null);
            } else if ("REGION_INDUSTRY".equals(trainingScopeType)) {
                row.setCustomerCode(null);
                row.setCustomerName(null);
            }
            if ("DAY".equals(granularity))
                fill(row, weather.get(key(row.getRegionCode(), group.bucket)));
            else
                fill(row, weatherGroups.get(key(row.getRegionCode(), group.bucket)));
            result.add(row);
        }
        return result.stream().sorted(Comparator.comparing(ModelTrainFeatureDataTb::getStatDate).thenComparing(ModelTrainFeatureDataTb::getCustomerCode, Comparator.nullsFirst(String::compareTo)))
                .toList();
    }

    private ModelTrainFeatureDataTb row(DataDailySalesTb sale, String statDate, String granularity, long id) {
        ModelTrainFeatureDataTb row = new ModelTrainFeatureDataTb();
        row.setId(id);
        row.setStatDate(statDate);
        row.setTimeGranularity(granularity);
        row.setGasSales(decimal(sale.getGasSales()));
        row.setRegionCode(sale.getRegionCode());
        row.setRegionName(sale.getRegionName());
        row.setIndustryCode(sale.getIndustryCode());
        row.setIndustryName(sale.getIndustryName());
        row.setCustomerCode(sale.getCustomerCode());
        row.setCustomerName(sale.getCustomerName());
        return row;
    }

    private void fill(ModelTrainFeatureDataTb r, DataRegionWeatherDailyTb w) {
        if (w == null)
            return;
        r.setFeature001(d(w.getTempAvg()));
        r.setFeature002(d(w.getTempMax()));
        r.setFeature003(d(w.getTempMin()));
        r.setFeature004(d(w.getHdd()));
        r.setFeature005(w.getTempMin() == null ? null : w.getTempMin().compareTo(BigDecimal.valueOf(-10)) < 0 ? 1D : 0D);
        r.setFeature006(d(w.getTempMax()));
        r.setFeature007(d(w.getTempMin()));
        r.setFeature008(d(w.getTempAvg()));
        r.setFeature009(d(w.getFeelsLikeMax()));
        r.setFeature010(d(w.getFeelsLikeMin()));
        r.setFeature011(d(w.getFeelsLikeAvg()));
        r.setFeature012(d(w.getDewPoint()));
        r.setFeature013(d(w.getHumidity()));
        r.setFeature014(d(w.getPrecipitation()));
        r.setFeature015(d(w.getWindGust()));
        r.setFeature016(d(w.getWindSpeed()));
        r.setFeature017(d(w.getSeaLevelPressure()));
        r.setFeature018(d(w.getCloudCover()));
        r.setFeature019(d(w.getVisibility()));
        r.setFeature020(d(w.getSolarRadiation()));
        r.setFeature021(d(w.getSolarEnergy()));
        r.setFeature022(d(w.getUvIndex()));
    }

    private void fill(ModelTrainFeatureDataTb r, WeatherAggregate w) {
        if (w == null)
            return;
        r.setFeature001(w.avg("tempAvg"));
        r.setFeature002(w.max("tempMax"));
        r.setFeature003(w.min("tempMin"));
        r.setFeature004(w.sum("hdd"));
        r.setFeature005((double) w.extremeColdDays);
        r.setFeature006(w.max("tempMax"));
        r.setFeature007(w.min("tempMin"));
        r.setFeature008(w.avg("tempAvg"));
        r.setFeature009(w.max("feelsLikeMax"));
        r.setFeature010(w.min("feelsLikeMin"));
        r.setFeature011(w.avg("feelsLikeAvg"));
        r.setFeature012(w.avg("dewPoint"));
        r.setFeature013(w.avg("humidity"));
        r.setFeature014(w.sum("precipitation"));
        r.setFeature015(w.max("windGust"));
        r.setFeature016(w.avg("windSpeed"));
        r.setFeature017(w.avg("seaLevelPressure"));
        r.setFeature018(w.avg("cloudCover"));
        r.setFeature019(w.avg("visibility"));
        r.setFeature020(w.avg("solarRadiation"));
        r.setFeature021(w.sum("solarEnergy"));
        r.setFeature022(w.max("uvIndex"));
    }

    private String normalizedGranularity(String value) {
        String result = safe(value).toUpperCase();
        return Set.of("DAY", "TENDAY", "MONTH").contains(result) ? result : "DAY";
    }
    /** 训练汇总维度由本次训练目标决定，而不是由模型配置决定。 */
    private String trainingScope(ModelTrainConfigTb config) {
        if (TextUtils.hasText(config.getCustomerCode()))
            return "CUSTOMER";
        if (TextUtils.hasText(config.getIndustryCode()))
            return "REGION_INDUSTRY";
        return "REGION";
    }
    private LocalDate bucket(LocalDate value, String granularity) {
        return "MONTH".equals(granularity)
                ? value.withDayOfMonth(1)
                : "TENDAY".equals(granularity) ? value.withDayOfMonth(value.getDayOfMonth() <= 10 ? 1 : value.getDayOfMonth() <= 20 ? 11 : 21) : value;
    }
    private LocalDate date(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new BusinessException("日期格式必须为yyyy-MM-dd：" + value);
        }
    }
    private LocalDate localDate(Date value) {
        return new java.sql.Date(value.getTime()).toLocalDate();
    }
    private BigDecimal decimal(String value) {
        try {
            return TextUtils.hasText(value) ? new BigDecimal(value.trim()) : BigDecimal.ZERO;
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
    private Double d(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
    private String key(String region, LocalDate date) {
        return safe(region) + '|' + date;
    }
    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
    private Set<String> set(String value) {
        Set<String> result = new LinkedHashSet<>();
        add(result, value);
        return result;
    }
    private void add(Set<String> values, String value) {
        if (TextUtils.hasText(value))
            values.add(value.trim());
    }

    private record Scope(Set<String> regions, Set<String> industries, Set<String> customers) {
    }
    private static final class SalesAggregate {
        private final DataDailySalesTb sample;
        private final LocalDate bucket;
        private BigDecimal value = BigDecimal.ZERO;
        private SalesAggregate(DataDailySalesTb sample, LocalDate bucket) {
            this.sample = sample;
            this.bucket = bucket;
        }
    }
    private static final class WeatherAggregate {
        private final Map<String, List<Double>> values = new LinkedHashMap<>();
        private int extremeColdDays;
        private void add(DataRegionWeatherDailyTb w) {
            put("tempMax", w.getTempMax());
            put("tempMin", w.getTempMin());
            put("tempAvg", w.getTempAvg());
            put("hdd", w.getHdd());
            put("feelsLikeMax", w.getFeelsLikeMax());
            put("feelsLikeMin", w.getFeelsLikeMin());
            put("feelsLikeAvg", w.getFeelsLikeAvg());
            put("dewPoint", w.getDewPoint());
            put("humidity", w.getHumidity());
            put("precipitation", w.getPrecipitation());
            put("windGust", w.getWindGust());
            put("windSpeed", w.getWindSpeed());
            put("seaLevelPressure", w.getSeaLevelPressure());
            put("cloudCover", w.getCloudCover());
            put("visibility", w.getVisibility());
            put("solarRadiation", w.getSolarRadiation());
            put("solarEnergy", w.getSolarEnergy());
            put("uvIndex", w.getUvIndex());
            if (w.getTempMin() != null && w.getTempMin().compareTo(BigDecimal.valueOf(-10)) < 0)
                extremeColdDays++;
        }
        private void put(String key, BigDecimal value) {
            if (value != null)
                values.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value.doubleValue());
        }
        private Double avg(String key) {
            List<Double> v = values.get(key);
            return v == null || v.isEmpty() ? null : v.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        }
        private Double sum(String key) {
            List<Double> v = values.get(key);
            return v == null || v.isEmpty() ? null : v.stream().mapToDouble(Double::doubleValue).sum();
        }
        private Double max(String key) {
            List<Double> v = values.get(key);
            return v == null || v.isEmpty() ? null : v.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        }
        private Double min(String key) {
            List<Double> v = values.get(key);
            return v == null || v.isEmpty() ? null : v.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        }
    }
}
