package com.gas.forecast.business.service.impl;

import com.gas.forecast.business.dto.request.*;
import com.gas.forecast.business.dto.response.ModelTrainFeatureDataResponse;
import com.gas.forecast.business.dto.response.ModelTrainFeatureValueResponse;
import com.gas.forecast.business.service.ModelTrainFeatureDataService;
import com.gas.forecast.business.service.TrainingDatasetAssembler;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.core.PageInfoDTO;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 动态训练数据预览，不再读写训练特征宽表。 */
@Service
@RequiredArgsConstructor
public class ModelTrainFeatureDataServiceImpl implements ModelTrainFeatureDataService {
    private final TrainingDatasetAssembler assembler;

    @Override
    public PageInfoDTO<ModelTrainFeatureDataResponse> listPage(ModelTrainFeatureDataPageRequest request) {
        int page = request.page() == null ? 1 : request.page();
        int size = request.size() == null ? 20 : request.size();
        ModelConfigTb model = new ModelConfigTb();
        model.setTimeGranularity(TextUtils.hasText(request.timeGranularity()) ? request.timeGranularity() : "DAY");
        ModelTrainConfigTb config = new ModelTrainConfigTb();
        config.setRegionCode(request.regionCode());
        config.setIndustryCode(request.industryCode());
        config.setCustomerCode(request.customerCode());
        boolean range = TextUtils.hasText(request.statDateStart()) || TextUtils.hasText(request.statDateEnd());
        if (range) {
            if (!TextUtils.hasText(request.statDateStart()) || !TextUtils.hasText(request.statDateEnd()))
                throw new BusinessException("必须同时填写开始和结束日期");
            config.setTrainMode("RANGE");
            config.setTrainStartDate(request.statDateStart());
            config.setTrainEndDate(request.statDateEnd());
        } else {
            config.setTrainMode("RECENT");
            config.setRecentPeriods(Math.max(size * page, 20));
        }
        List<ModelTrainFeatureDataTb> rows = assembler.assemble(config, model);
        int from = Math.min((page - 1) * size, rows.size());
        int to = Math.min(from + size, rows.size());
        Map<String, String> mappings = mappings();
        return PageInfoDTO.of(rows.subList(from, to).stream().map(row -> response(row, mappings)).toList(), rows.size(), page, size);
    }

    @Override
    public ModelTrainFeatureDataResponse create(ModelTrainFeatureDataCreateRequest request) {
        throw readonly();
    }
    @Override
    public ModelTrainFeatureDataResponse update(ModelTrainFeatureDataUpdateRequest request) {
        throw readonly();
    }
    @Override
    public void delete(Long id) {
        throw readonly();
    }
    private BusinessException readonly() {
        return new BusinessException("训练数据由销量、天气和模型配置动态生成，不支持手工维护");
    }

    private ModelTrainFeatureDataResponse response(ModelTrainFeatureDataTb row, Map<String, String> mappings) {
        Map<String, Double> values = values(row);
        List<ModelTrainFeatureValueResponse> details = values.entrySet().stream()
                .map(entry -> new ModelTrainFeatureValueResponse(entry.getKey(), mappings.getOrDefault(entry.getKey(), "-"), entry.getValue())).toList();
        return new ModelTrainFeatureDataResponse(row.getId(), row.getStatDate(), row.getTimeGranularity(), row.getRegionCode(), row.getRegionName(), row.getCustomerCode(), row.getCustomerName(),
                row.getIndustryCode(), row.getIndustryName(), row.getGasSales(), row.getFeature001(), row.getFeature002(), row.getFeature003(), row.getFeature004(), row.getFeature005(),
                row.getFeature006(), row.getFeature007(), row.getFeature008(), row.getFeature009(), row.getFeature010(), values, details, null, null);
    }

    private Map<String, String> mappings() {
        return Map.ofEntries(Map.entry("feature001", "avg_temp"), Map.entry("feature002", "max_temp"), Map.entry("feature003", "min_temp"), Map.entry("feature004", "hdd"),
                Map.entry("feature005", "extreme_cold_days"), Map.entry("feature006", "temp_max"), Map.entry("feature007", "temp_min"), Map.entry("feature008", "temp_avg"),
                Map.entry("feature009", "feels_like_max"), Map.entry("feature010", "feels_like_min"), Map.entry("feature011", "feels_like_avg"), Map.entry("feature012", "dew_point"),
                Map.entry("feature013", "humidity"), Map.entry("feature014", "precipitation"), Map.entry("feature015", "wind_gust"), Map.entry("feature016", "wind_speed"),
                Map.entry("feature017", "sea_level_pressure"), Map.entry("feature018", "cloud_cover"), Map.entry("feature019", "visibility"), Map.entry("feature020", "solar_radiation"),
                Map.entry("feature021", "solar_energy"), Map.entry("feature022", "uv_index"));
    }
    private Map<String, Double> values(ModelTrainFeatureDataTb row) {
        Map<String, Double> result = new LinkedHashMap<>();
        for (int i = 1; i <= 200; i++) {
            String property = String.format("feature%03d", i);
            try {
                Object value = ModelTrainFeatureDataTb.class.getMethod("get" + Character.toUpperCase(property.charAt(0)) + property.substring(1)).invoke(row);
                if (value instanceof Number number)
                    result.put(property, number.doubleValue());
            } catch (ReflectiveOperationException ignored) {
                break;
            }
        }
        return result;
    }
}
