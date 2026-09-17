package com.gas.forecast.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.business.dto.resp.BacktestDetailRespDTO;
import com.gas.forecast.business.dto.resp.CustomerForecastPointRespDTO;
import com.gas.forecast.business.dto.resp.DimensionItemRespDTO;
import com.gas.forecast.business.dto.resp.FeatureRankRespDTO;
import com.gas.forecast.business.dto.resp.ForecastDashboardRespDTO;
import com.gas.forecast.business.dto.resp.ForecastDimensionRespDTO;
import com.gas.forecast.business.dto.resp.ForecastPointRespDTO;
import com.gas.forecast.business.dto.resp.ForecastSummaryRespDTO;
import com.gas.forecast.business.dto.resp.ModelRankRespDTO;
import com.gas.forecast.business.service.GasForecastService;
import com.gas.forecast.dao.domain.BaseCustomerTb;
import com.gas.forecast.dao.domain.BaseRegionTb;
import com.gas.forecast.dao.domain.ModelForecastBatchTb;
import com.gas.forecast.dao.domain.ModelForecastResultTb;
import com.gas.forecast.dao.domain.ModelTrainBacktestTb;
import com.gas.forecast.dao.domain.ModelTrainBatchTb;
import com.gas.forecast.dao.mapper.BaseCustomerTbMapper;
import com.gas.forecast.dao.mapper.BaseRegionTbMapper;
import com.gas.forecast.dao.mapper.ModelForecastBatchTbMapper;
import com.gas.forecast.dao.mapper.ModelForecastResultTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainBacktestTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainBatchTbMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class GasForecastServiceImpl implements GasForecastService {

    private static final BigDecimal LOWER_RATIO = new BigDecimal("0.95");
    private static final BigDecimal UPPER_RATIO = new BigDecimal("1.05");

    private final BaseRegionTbMapper baseRegionTbMapper;
    private final BaseCustomerTbMapper baseCustomerTbMapper;
    private final ModelTrainBatchTbMapper modelTrainBatchTbMapper;
    private final ModelForecastBatchTbMapper modelForecastBatchTbMapper;
    private final ModelForecastResultTbMapper modelForecastResultTbMapper;
    private final ModelTrainBacktestTbMapper modelTrainBacktestTbMapper;

    public GasForecastServiceImpl(BaseRegionTbMapper baseRegionTbMapper,
                                  BaseCustomerTbMapper baseCustomerTbMapper,
                                  ModelTrainBatchTbMapper modelTrainBatchTbMapper,
                                  ModelForecastBatchTbMapper modelForecastBatchTbMapper,
                                  ModelForecastResultTbMapper modelForecastResultTbMapper,
                                  ModelTrainBacktestTbMapper modelTrainBacktestTbMapper) {
        this.baseRegionTbMapper = baseRegionTbMapper;
        this.baseCustomerTbMapper = baseCustomerTbMapper;
        this.modelTrainBatchTbMapper = modelTrainBatchTbMapper;
        this.modelForecastBatchTbMapper = modelForecastBatchTbMapper;
        this.modelForecastResultTbMapper = modelForecastResultTbMapper;
        this.modelTrainBacktestTbMapper = modelTrainBacktestTbMapper;
    }

    @Override
    public List<String> listProvinces() {
        return selectProvinceRegions().stream()
                .map(BaseRegionTb::getRegionName)
                .toList();
    }

    @Override
    public List<ForecastSummaryRespDTO> listSummaries() {
        Map<String, ModelTrainBatchTb> trainBatchByRegionCode = selectWinnerTrainBatches().stream()
                .collect(Collectors.toMap(ModelTrainBatchTb::getRegionCode, item -> item, (left, right) -> left));
        return selectProvinceRegions().stream()
                .map(province -> toSummaryDTO(trainBatchByRegionCode.get(province.getRegionCode()), province))
                .toList();
    }

    @Override
    public ForecastDimensionRespDTO listDimensions(String areaCode, String provinceCode) {
        List<BaseRegionTb> provinces = selectProvinceRegions().stream()
                .filter(item -> !StringUtils.hasText(areaCode) || item.getRegionCode().equals(areaCode))
                .toList();
        List<BaseCustomerTb> customers = selectCustomers(areaCode, provinceCode);

        return new ForecastDimensionRespDTO(
                List.of(),
                provinces.stream().map(this::toProvinceDimensionItemRespDTO).toList(),
                customers.stream().map(this::toCustomerDimensionItemRespDTO).toList()
        );
    }

    @Override
    public ForecastDashboardRespDTO getDashboard(String province) {
        BaseRegionTb targetProvince = StringUtils.hasText(province)
                ? selectProvinceByName(province)
                : selectProvinceRegions().stream()
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("数据库中暂无预测数据"));
        return dashboard(targetProvince, null);
    }

    @Override
    public ForecastDashboardRespDTO getDashboardByCode(String provinceCode, String customerCode) {
        BaseRegionTb province = selectProvinceRegions().stream()
                .filter(item -> item.getRegionCode().equals(provinceCode))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("未找到省份编码: " + provinceCode));
        return dashboard(province, customerCode);
    }

    private ForecastDashboardRespDTO dashboard(BaseRegionTb province, String customerCode) {
        ModelTrainBatchTb trainBatch = selectWinnerTrainBatch(province);
        List<ModelForecastResultTb> forecastResults = selectForecastResults(province);
        return new ForecastDashboardRespDTO(
                toSummaryDTO(trainBatch, province),
                forecastResults.stream().map(this::toForecastPointRespDTO).toList(),
                toCustomerForecastPointRespDTOs(province, customerCode, forecastResults),
                selectModelRanks(province).stream().map(this::toModelRankRespDTO).toList(),
                List.<FeatureRankRespDTO>of(),
                selectBacktestDetails(province).stream().map(this::toBacktestDetailRespDTO).toList()
        );
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
                .filter(item -> !StringUtils.hasText(areaCode) || item.getRegionCode().equals(areaCode))
                .filter(item -> !StringUtils.hasText(provinceCode) || item.getRegionCode().equals(provinceCode))
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

    private List<ModelTrainBatchTb> selectWinnerTrainBatches() {
        return modelTrainBatchTbMapper.selectList(Wrappers.<ModelTrainBatchTb>lambdaQuery()
                .likeRight(ModelTrainBatchTb::getBatchNo, "WGTRAIN-"));
    }

    private ModelTrainBatchTb selectWinnerTrainBatch(BaseRegionTb province) {
        return modelTrainBatchTbMapper.selectOne(Wrappers.<ModelTrainBatchTb>lambdaQuery()
                .eq(ModelTrainBatchTb::getRegionCode, province.getRegionCode())
                .likeRight(ModelTrainBatchTb::getBatchNo, "WGTRAIN-")
                .last("limit 1"));
    }

    private List<ModelTrainBatchTb> selectModelRanks(BaseRegionTb province) {
        return modelTrainBatchTbMapper.selectList(Wrappers.<ModelTrainBatchTb>lambdaQuery()
                .eq(ModelTrainBatchTb::getRegionCode, province.getRegionCode())
                .likeRight(ModelTrainBatchTb::getBatchNo, "WGTRAIN-")
                .last("limit 10"));
    }

    private List<ModelTrainBacktestTb> selectBacktestDetails(BaseRegionTb province) {
        return modelTrainBacktestTbMapper.selectList(Wrappers.<ModelTrainBacktestTb>lambdaQuery()
                .eq(ModelTrainBacktestTb::getTrainBatchNo, "WGTRAIN-" + province.getRegionName())
                .orderByAsc(ModelTrainBacktestTb::getTrainDate));
    }

    private List<ModelForecastResultTb> selectForecastResults(BaseRegionTb province) {
        Optional<ModelForecastBatchTb> batch = modelForecastBatchTbMapper.selectList(Wrappers.<ModelForecastBatchTb>lambdaQuery()
                        .eq(ModelForecastBatchTb::getRegionCode, province.getRegionCode())
                        .orderByDesc(ModelForecastBatchTb::getCreatedAt)
                        .orderByDesc(ModelForecastBatchTb::getId))
                .stream()
                .findFirst();
        if (batch.isEmpty()) {
            return List.of();
        }
        return modelForecastResultTbMapper.selectList(Wrappers.<ModelForecastResultTb>lambdaQuery()
                .eq(ModelForecastResultTb::getForecastBatchNo, batch.get().getBatchNo())
                .orderByAsc(ModelForecastResultTb::getForecastDate));
    }

    private ForecastSummaryRespDTO toSummaryDTO(ModelTrainBatchTb trainBatch, BaseRegionTb province) {
        return new ForecastSummaryRespDTO(
                province.getRegionCode(),
                province.getRegionName(),
                trainBatch != null && StringUtils.hasText(trainBatch.getBestModel()) ? trainBatch.getBestModel() : "-",
                trainBatch == null ? null : trainBatch.getMape(),
                trainBatch == null ? null : trainBatch.getWmape(),
                trainBatch == null ? null : trainBatch.getRmse(),
                null,
                15,
                "用户提供未来气象",
                chartPath(province.getRegionName())
        );
    }

    private DimensionItemRespDTO toProvinceDimensionItemRespDTO(BaseRegionTb item) {
        return new DimensionItemRespDTO(item.getRegionCode(), item.getRegionName(), null, "province");
    }

    private DimensionItemRespDTO toCustomerDimensionItemRespDTO(BaseCustomerTb item) {
        return new DimensionItemRespDTO(item.getCustomerCode(), item.getCustomerName(), item.getRegionCode(), item.getIndustryName());
    }

    private ForecastPointRespDTO toForecastPointRespDTO(ModelForecastResultTb point) {
        LocalDate forecastDate = toLocalDate(point.getForecastDate());
        BigDecimal prediction = point.getForecastValue();
        return new ForecastPointRespDTO(
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
                multiply(prediction, UPPER_RATIO)
        );
    }

    private List<CustomerForecastPointRespDTO> toCustomerForecastPointRespDTOs(BaseRegionTb province,
                                                                       String customerCode,
                                                                       List<ModelForecastResultTb> forecastResults) {
        LambdaQueryWrapper<BaseCustomerTb> wrapper = Wrappers.<BaseCustomerTb>lambdaQuery()
                .eq(BaseCustomerTb::getRegionCode, province.getRegionCode())
                .orderByAsc(BaseCustomerTb::getId);
        if (StringUtils.hasText(customerCode)) {
            wrapper.eq(BaseCustomerTb::getCustomerCode, customerCode);
        }
        List<BaseCustomerTb> customers = baseCustomerTbMapper.selectList(wrapper);
        return customers.stream()
                .flatMap(customer -> forecastResults.stream().map(point -> toCustomerForecastPointRespDTO(customer, point)))
                .toList();
    }

    private CustomerForecastPointRespDTO toCustomerForecastPointRespDTO(BaseCustomerTb customer, ModelForecastResultTb point) {
        LocalDate forecastDate = toLocalDate(point.getForecastDate());
        BigDecimal prediction = multiply(point.getForecastValue(), customerRatio(customer.getIndustryName()));
        return new CustomerForecastPointRespDTO(
                customer.getCustomerCode(),
                customer.getCustomerName(),
                customer.getIndustryName(),
                forecastDate,
                tendayLabel(forecastDate),
                prediction,
                multiply(prediction, LOWER_RATIO),
                multiply(prediction, UPPER_RATIO)
        );
    }

    private ModelRankRespDTO toModelRankRespDTO(ModelTrainBatchTb rank) {
        return new ModelRankRespDTO(
                StringUtils.hasText(rank.getBestModel()) ? rank.getBestModel() : "winner-agent",
                "ml",
                rank.getMape(),
                rank.getWmape(),
                rank.getRmse(),
                rank.getMae(),
                rank.getR2(),
                null
        );
    }

    private BacktestDetailRespDTO toBacktestDetailRespDTO(ModelTrainBacktestTb detail) {
        LocalDate trainDate = toLocalDate(detail.getTrainDate());
        BigDecimal absoluteError = detail.getActualValue() == null || detail.getPredictedValue() == null
                ? null
                : detail.getActualValue().subtract(detail.getPredictedValue()).abs();
        return new BacktestDetailRespDTO(
                "winner-agent",
                trainDate == null ? null : trainDate.getYear() + "-" + (trainDate.getYear() + 1),
                trainDate,
                detail.getActualValue(),
                detail.getPredictedValue(),
                absoluteError,
                apePct(detail.getActualValue(), absoluteError)
        );
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
                    Integer.parseInt(text.substring(6, 8))
            );
        }
        return LocalDate.parse(text);
    }
}
