package com.gas.forecast.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.business.dto.response.BacktestDetailResponse;
import com.gas.forecast.business.dto.response.CustomerForecastPointResponse;
import com.gas.forecast.business.dto.response.DimensionItemResponse;
import com.gas.forecast.business.dto.response.FeatureRankResponse;
import com.gas.forecast.business.dto.response.ForecastDashboardResponse;
import com.gas.forecast.business.dto.response.ForecastDimensionResponse;
import com.gas.forecast.business.dto.response.ForecastPointResponse;
import com.gas.forecast.business.dto.response.ForecastSummaryResponse;
import com.gas.forecast.business.dto.response.ModelRankResponse;
import com.gas.forecast.business.service.GasForecastService;
import com.gas.forecast.dao.domain.BaseCustomerTb;
import com.gas.forecast.dao.domain.BaseRegionTb;
import com.gas.forecast.dao.domain.ModelForecastRecordTb;
import com.gas.forecast.dao.domain.ModelForecastResultTb;
import com.gas.forecast.dao.domain.ModelTrainBacktestTb;
import com.gas.forecast.dao.domain.ModelTrainRecordTb;
import com.gas.forecast.dao.mapper.BaseCustomerTbMapper;
import com.gas.forecast.dao.mapper.BaseRegionTbMapper;
import com.gas.forecast.dao.mapper.ModelForecastRecordTbMapper;
import com.gas.forecast.dao.mapper.ModelForecastResultTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainBacktestTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainRecordTbMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class GasForecastServiceImpl implements GasForecastService {

    private static final BigDecimal LOWER_RATIO = new BigDecimal("0.95");
    private static final BigDecimal UPPER_RATIO = new BigDecimal("1.05");

    private final BaseRegionTbMapper baseRegionTbMapper;
    private final BaseCustomerTbMapper baseCustomerTbMapper;
    private final ModelTrainRecordTbMapper modelTrainDetailTbMapper;
    private final ModelForecastRecordTbMapper forecastRecordMapper;
    private final ModelForecastResultTbMapper modelForecastResultTbMapper;
    private final ModelTrainBacktestTbMapper modelTrainBacktestTbMapper;

    @Override
    public List<String> listProvinces() {
        return selectProvinceRegions().stream().map(BaseRegionTb::getRegionName).toList();
    }

    @Override
    public List<ForecastSummaryResponse> listSummaries() {
        Map<String, ModelTrainRecordTb> trainDetailByRegionCode = selectWinnerTrainDetails().stream()
                .collect(Collectors.toMap(ModelTrainRecordTb::getRegionCode, item -> item, (left, right) -> left));
        return selectProvinceRegions().stream()
                .map(province -> toSummaryDTO(trainDetailByRegionCode.get(province.getRegionCode()), province))
                .toList();
    }

    @Override
    public ForecastDimensionResponse listDimensions(String areaCode, String provinceCode) {
        List<BaseRegionTb> provinces = selectProvinceRegions().stream()
                .filter(item ->
                        !StringUtils.hasText(areaCode) || item.getRegionCode().equals(areaCode))
                .toList();
        List<BaseCustomerTb> customers = selectCustomers(areaCode, provinceCode);

        return new ForecastDimensionResponse(
                List.of(),
                provinces.stream().map(this::toProvinceDimensionItemResponse).toList(),
                customers.stream().map(this::toCustomerDimensionItemResponse).toList());
    }

    @Override
    public ForecastDashboardResponse getDashboard(String province) {
        BaseRegionTb targetProvince = StringUtils.hasText(province)
                ? selectProvinceByName(province)
                : selectProvinceRegions().stream()
                        .findFirst()
                        .orElseThrow(() -> new NoSuchElementException("数据库中暂无预测数据"));
        return dashboard(targetProvince, null);
    }

    @Override
    public ForecastDashboardResponse getDashboardByCode(String provinceCode, String customerCode) {
        BaseRegionTb province = selectProvinceRegions().stream()
                .filter(item -> item.getRegionCode().equals(provinceCode))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("未找到省份编码: " + provinceCode));
        return dashboard(province, customerCode);
    }

    private ForecastDashboardResponse dashboard(BaseRegionTb province, String customerCode) {
        ModelTrainRecordTb trainDetail = selectWinnerTrainDetail(province);
        List<ModelForecastResultTb> forecastResults = selectForecastResults(province);
        return new ForecastDashboardResponse(
                toSummaryDTO(trainDetail, province),
                forecastResults.stream().map(this::toForecastPointResponse).toList(),
                toCustomerForecastPointResponses(province, customerCode, forecastResults),
                selectModelRanks(province).stream()
                        .map(this::toModelRankResponse)
                        .toList(),
                List.<FeatureRankResponse>of(),
                selectBacktestDetails(province, trainDetail).stream()
                        .map(this::toBacktestDetailResponse)
                        .toList());
    }

    private List<BaseRegionTb> selectProvinceRegions() {
        return baseRegionTbMapper.selectList(Wrappers.<BaseRegionTb>lambdaQuery()
                .eq(BaseRegionTb::getRemark, "winner-agent模拟省份")
                .orderByAsc(BaseRegionTb::getId));
    }

    private BaseRegionTb selectProvinceByName(String province) {
        return selectProvinceRegions().stream()
                .filter(item -> item.getRegionName().equals(province))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("未找到省份预测数据: " + province));
    }

    private List<BaseCustomerTb> selectCustomers(String areaCode, String provinceCode) {
        List<String> provinceCodes = selectProvinceRegions().stream()
                .filter(item ->
                        !StringUtils.hasText(areaCode) || item.getRegionCode().equals(areaCode))
                .filter(item -> !StringUtils.hasText(provinceCode)
                        || item.getRegionCode().equals(provinceCode))
                .map(BaseRegionTb::getRegionCode)
                .toList();
        if ((StringUtils.hasText(areaCode) || StringUtils.hasText(provinceCode)) && provinceCodes.isEmpty()) {
            return List.of();
        }

        LambdaQueryWrapper<BaseCustomerTb> wrapper = Wrappers.<BaseCustomerTb>lambdaQuery()
                .orderByAsc(BaseCustomerTb::getRegionCode)
                .orderByAsc(BaseCustomerTb::getId);
        if (!provinceCodes.isEmpty()) {
            wrapper.in(BaseCustomerTb::getRegionCode, provinceCodes);
        }
        return baseCustomerTbMapper.selectList(wrapper);
    }

    private List<ModelTrainRecordTb> selectWinnerTrainDetails() {
        return modelTrainDetailTbMapper.selectList(Wrappers.<ModelTrainRecordTb>lambdaQuery()
                .likeRight(ModelTrainRecordTb::getBatchNo, "WGTRAIN-")
                .orderByDesc(ModelTrainRecordTb::getUpdatedAt)
                .orderByDesc(ModelTrainRecordTb::getId));
    }

    private ModelTrainRecordTb selectWinnerTrainDetail(BaseRegionTb province) {
        return modelTrainDetailTbMapper.selectOne(Wrappers.<ModelTrainRecordTb>lambdaQuery()
                .eq(ModelTrainRecordTb::getRegionCode, province.getRegionCode())
                .likeRight(ModelTrainRecordTb::getBatchNo, "WGTRAIN-")
                .orderByDesc(ModelTrainRecordTb::getUpdatedAt)
                .orderByDesc(ModelTrainRecordTb::getId)
                .last("limit 1"));
    }

    private List<ModelTrainRecordTb> selectModelRanks(BaseRegionTb province) {
        return modelTrainDetailTbMapper.selectList(Wrappers.<ModelTrainRecordTb>lambdaQuery()
                .eq(ModelTrainRecordTb::getRegionCode, province.getRegionCode())
                .likeRight(ModelTrainRecordTb::getBatchNo, "WGTRAIN-")
                .orderByDesc(ModelTrainRecordTb::getUpdatedAt)
                .orderByDesc(ModelTrainRecordTb::getId)
                .last("limit 10"));
    }

    private List<ModelTrainBacktestTb> selectBacktestDetails(BaseRegionTb province, ModelTrainRecordTb trainDetail) {
        String batchNo = trainDetail != null && StringUtils.hasText(trainDetail.getBatchNo())
                ? trainDetail.getBatchNo()
                : "WGTRAIN-" + province.getRegionName();
        return modelTrainBacktestTbMapper.selectList(Wrappers.<ModelTrainBacktestTb>lambdaQuery()
                .eq(ModelTrainBacktestTb::getTrainBatchNo, batchNo)
                .orderByAsc(ModelTrainBacktestTb::getTrainDate));
    }

    private List<ModelForecastResultTb> selectForecastResults(BaseRegionTb province) {
        ModelForecastRecordTb latest = forecastRecordMapper.selectOne(Wrappers.<ModelForecastRecordTb>lambdaQuery()
                .eq(ModelForecastRecordTb::getRegionCode, province.getRegionCode())
                .eq(ModelForecastRecordTb::getStatus, 2)
                .orderByDesc(ModelForecastRecordTb::getCreatedAt)
                .orderByDesc(ModelForecastRecordTb::getId)
                .last("limit 1"));
        String batchNo = latest == null ? "WGFC-" + province.getRegionName() : latest.getForecastBatchNo();
        return modelForecastResultTbMapper.selectList(Wrappers.<ModelForecastResultTb>lambdaQuery()
                .eq(ModelForecastResultTb::getForecastBatchNo, batchNo)
                .orderByAsc(ModelForecastResultTb::getForecastDate));
    }

    private ForecastSummaryResponse toSummaryDTO(ModelTrainRecordTb trainDetail, BaseRegionTb province) {
        return new ForecastSummaryResponse(
                province.getRegionCode(),
                province.getRegionName(),
                trainDetail != null && StringUtils.hasText(trainDetail.getBestModel())
                        ? trainDetail.getBestModel()
                        : "-",
                trainDetail == null ? null : trainDetail.getMape(),
                trainDetail == null ? null : trainDetail.getWmape(),
                trainDetail == null ? null : trainDetail.getRmse(),
                null,
                15,
                "用户提供未来气象",
                chartPath(province.getRegionName()));
    }

    private DimensionItemResponse toProvinceDimensionItemResponse(BaseRegionTb item) {
        return new DimensionItemResponse(item.getRegionCode(), item.getRegionName(), null, "province");
    }

    private DimensionItemResponse toCustomerDimensionItemResponse(BaseCustomerTb item) {
        return new DimensionItemResponse(
                item.getCustomerCode(), item.getCustomerName(), item.getRegionCode(), item.getIndustryName());
    }

    private ForecastPointResponse toForecastPointResponse(ModelForecastResultTb point) {
        LocalDate forecastDate = toLocalDate(point.getForecastDate());
        BigDecimal prediction = point.getForecastValue();
        return new ForecastPointResponse(
                forecastDate,
                tendayLabel(forecastDate),
                null,
                null,
                null,
                null,
                null,
                "用户提供未来气象",
                prediction,
                multiply(prediction, LOWER_RATIO),
                multiply(prediction, UPPER_RATIO));
    }

    private List<CustomerForecastPointResponse> toCustomerForecastPointResponses(
            BaseRegionTb province, String customerCode, List<ModelForecastResultTb> forecastResults) {
        LambdaQueryWrapper<BaseCustomerTb> wrapper = Wrappers.<BaseCustomerTb>lambdaQuery()
                .eq(BaseCustomerTb::getRegionCode, province.getRegionCode())
                .orderByAsc(BaseCustomerTb::getId);
        if (StringUtils.hasText(customerCode)) {
            wrapper.eq(BaseCustomerTb::getCustomerCode, customerCode);
        }
        List<BaseCustomerTb> customers = baseCustomerTbMapper.selectList(wrapper);
        return customers.stream()
                .flatMap(customer ->
                        forecastResults.stream().map(point -> toCustomerForecastPointResponse(customer, point)))
                .toList();
    }

    private CustomerForecastPointResponse toCustomerForecastPointResponse(
            BaseCustomerTb customer, ModelForecastResultTb point) {
        LocalDate forecastDate = toLocalDate(point.getForecastDate());
        BigDecimal prediction = multiply(point.getForecastValue(), customerRatio(customer.getIndustryName()));
        return new CustomerForecastPointResponse(
                customer.getCustomerCode(),
                customer.getCustomerName(),
                customer.getIndustryName(),
                forecastDate,
                tendayLabel(forecastDate),
                prediction,
                multiply(prediction, LOWER_RATIO),
                multiply(prediction, UPPER_RATIO));
    }

    private ModelRankResponse toModelRankResponse(ModelTrainRecordTb rank) {
        return new ModelRankResponse(
                StringUtils.hasText(rank.getBestModel()) ? rank.getBestModel() : "winner-agent",
                "ml",
                rank.getMape(),
                rank.getWmape(),
                rank.getRmse(),
                rank.getMae(),
                rank.getR2(),
                null);
    }

    private BacktestDetailResponse toBacktestDetailResponse(ModelTrainBacktestTb detail) {
        LocalDate trainDate = toLocalDate(detail.getTrainDate());
        BigDecimal absoluteError = detail.getActualValue() == null || detail.getPredictedValue() == null
                ? null
                : detail.getActualValue().subtract(detail.getPredictedValue()).abs();
        return new BacktestDetailResponse(
                "winner-agent",
                trainDate == null ? null : trainDate.getYear() + "-" + (trainDate.getYear() + 1),
                trainDate,
                detail.getActualValue(),
                detail.getPredictedValue(),
                absoluteError,
                apePct(detail.getActualValue(), absoluteError));
    }

    private String chartPath(String province) {
        return switch (province) {
            case "北京" -> "/assets/beijing_forecast.png";
            case "天津" -> "/assets/tianjin_forecast.png";
            case "山东" -> "/assets/shandong_forecast.png";
            case "山西" -> "/assets/shanxi_forecast.png";
            case "河北" -> "/assets/hebei_forecast.png";
            case "河南" -> "/assets/henan_forecast.png";
            default -> "/assets/shaanxi_forecast.png";
        };
    }

    private String tendayLabel(LocalDate date) {
        if (date == null) {
            return null;
        }
        return switch (date.getDayOfMonth()) {
            case 1 -> date.getMonthValue() + "月上旬";
            case 11 -> date.getMonthValue() + "月中旬";
            default -> date.getMonthValue() + "月下旬";
        };
    }

    private BigDecimal customerRatio(String industryName) {
        return switch (industryName) {
            case "城燃" -> new BigDecimal("0.46");
            case "工业" -> new BigDecimal("0.34");
            default -> new BigDecimal("0.20");
        };
    }

    private BigDecimal multiply(BigDecimal value, BigDecimal ratio) {
        return value == null ? null : value.multiply(ratio);
    }

    private BigDecimal apePct(BigDecimal actual, BigDecimal absoluteError) {
        if (actual == null || absoluteError == null || BigDecimal.ZERO.compareTo(actual) == 0) {
            return null;
        }
        return absoluteError.divide(actual, 8, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
    }

    private LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof Date date) {
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        String text = value.toString();
        if (text.length() == 8 && text.chars().allMatch(Character::isDigit)) {
            return LocalDate.of(
                    Integer.parseInt(text.substring(0, 4)),
                    Integer.parseInt(text.substring(4, 6)),
                    Integer.parseInt(text.substring(6, 8)));
        }
        return LocalDate.parse(text);
    }
}
