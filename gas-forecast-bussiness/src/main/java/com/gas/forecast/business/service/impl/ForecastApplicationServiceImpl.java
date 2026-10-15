package com.gas.forecast.business.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.business.dto.request.AutoTrainRequest;
import com.gas.forecast.business.dto.request.ModelForecastConfigSaveRequest;
import com.gas.forecast.business.dto.request.ModelForecastExecuteRequest;
import com.gas.forecast.business.enums.ModelForecastStatus;
import com.gas.forecast.business.service.AutoTrainService;
import com.gas.forecast.business.service.ForecastApplicationService;
import com.gas.forecast.business.service.ModelForecastManagementService;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.BaseCustomerTb;
import com.gas.forecast.dao.domain.DataDailySalesTb;
import com.gas.forecast.dao.domain.ModelForecastConfigTb;
import com.gas.forecast.dao.domain.ModelForecastRecordTb;
import com.gas.forecast.dao.domain.ModelForecastResultTb;
import com.gas.forecast.dao.domain.ModelConfigTb;
import com.gas.forecast.dao.domain.ModelTrainConfigTb;
import com.gas.forecast.dao.domain.ModelTrainRecordTb;
import com.gas.forecast.dao.mapper.BaseCustomerTbMapper;
import com.gas.forecast.dao.mapper.DataDailySalesTbMapper;
import com.gas.forecast.dao.mapper.ModelForecastConfigTbMapper;
import com.gas.forecast.dao.mapper.ModelForecastRecordTbMapper;
import com.gas.forecast.dao.mapper.ModelForecastResultTbMapper;
import com.gas.forecast.dao.mapper.ModelConfigTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainConfigTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainRecordTbMapper;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 预测应用编排：确保可用训练模型、维护预测配置并提交异步预测任务。 */
@Service
@RequiredArgsConstructor
public class ForecastApplicationServiceImpl implements ForecastApplicationService {
    private final AutoTrainService autoTrainService;
    private final ModelForecastManagementService forecastService;
    private final BaseCustomerTbMapper customerMapper;
    private final DataDailySalesTbMapper dailySalesMapper;
    private final ModelTrainConfigTbMapper trainConfigMapper;
    private final ModelConfigTbMapper modelConfigMapper;
    private final ModelTrainRecordTbMapper trainRecordMapper;
    private final ModelForecastConfigTbMapper forecastConfigMapper;
    private final ModelForecastRecordTbMapper forecastRecordMapper;
    private final ModelForecastResultTbMapper forecastResultMapper;

    @Override
    public Object start(ForecastCommand command) {
        validate(command);
        List<BaseCustomerTb> customers = customers(command);
        if (customers.isEmpty())
            throw new BusinessException("当前业务范围内没有找到可预测客户");
        List<Object> tasks = new ArrayList<>();
        List<Map<String, String>> failures = new ArrayList<>();
        for (BaseCustomerTb customer : customers) {
            try {
                tasks.add(startOne(command, customer));
            } catch (RuntimeException exception) {
                failures.add(Map.of("customerCode", customer.getCustomerCode(), "customerName", customer.getCustomerName(), "message", safeMessage(exception)));
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", customers.size());
        result.put("submitted", tasks.size());
        result.put("failed", failures.size());
        result.put("tasks", tasks);
        result.put("failures", failures);
        result.put("status", tasks.isEmpty() ? "FAILED" : "EXECUTING");
        return result;
    }

    @Override
    public Object status(String forecastBatchNo) {
        ModelForecastRecordTb record = record(forecastBatchNo);
        long points = forecastResultMapper.selectCount(Wrappers.<ModelForecastResultTb>lambdaQuery().eq(ModelForecastResultTb::getForecastBatchNo, forecastBatchNo));
        return Map.of("forecastBatchNo", forecastBatchNo, "status", statusName(record.getStatus()), "forecastStartDate", record.getForecastStartDate(), "forecastHorizon",
                record.getForecastHorizon(), "completedPoints", points, "updatedAt", record.getUpdatedAt());
    }

    @Override
    public Object result(String forecastBatchNo, boolean includePoints) {
        ModelForecastRecordTb record = record(forecastBatchNo);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("record", record);
        if (includePoints)
            result.put("points", forecastResultMapper.selectList(Wrappers.<ModelForecastResultTb>lambdaQuery().eq(ModelForecastResultTb::getForecastBatchNo, forecastBatchNo)
                    .orderByAsc(ModelForecastResultTb::getForecastDate)));
        return result;
    }

    private Object startOne(ForecastCommand command, BaseCustomerTb customer) {
        ModelTrainConfigTb config = trainConfigMapper.selectOne(Wrappers.<ModelTrainConfigTb>lambdaQuery().eq(ModelTrainConfigTb::getAgentCode, command.agentCode())
                .eq(ModelTrainConfigTb::getCustomerCode, customer.getCustomerCode()).eq(ModelTrainConfigTb::getEnabled, 1).orderByDesc(ModelTrainConfigTb::getUpdatedAt)
                .orderByDesc(ModelTrainConfigTb::getId).last("limit 1"));
        ModelTrainRecordTb trained = config == null ? null
                : trainRecordMapper.selectOne(Wrappers.<ModelTrainRecordTb>lambdaQuery().eq(ModelTrainRecordTb::getTrainConfigId, config.getId()).eq(ModelTrainRecordTb::getStatus, "SUCCESS")
                        .orderByDesc(ModelTrainRecordTb::getUpdatedAt).last("limit 1"));
        if (command.forceRetrain() || trained == null) {
            if (!command.autoTrain())
                throw new BusinessException("客户没有可用训练结果，请先训练或设置autoTrain=true");
            AutoTrainRequest train = new AutoTrainRequest();
            train.setAgentCode(command.agentCode());
            train.setCustomerCode(customer.getCustomerCode());
            train.setTrainMode(command.trainMode());
            train.setRecentPeriods(command.trainingPeriods());
            train.setTrainStartDate(command.trainingStartDate());
            train.setTrainEndDate(command.trainingEndDate());
            train.setForceTrain(command.forceRetrain());
            train.setTriggerSource("MCP");
            var response = autoTrainService.execute(train);
            ModelTrainConfigTb pendingConfig = trainConfigMapper.selectById(response.getTrainConfigId());
            if (pendingConfig != null)
                saveForecastConfig(command, customer, pendingConfig, "MCP等待训练后自动预测");
            return Map.of("customerCode", customer.getCustomerCode(), "customerName", customer.getCustomerName(), "status", "WAITING_TRAINING", "trainBatchNo",
                    response.getTrainBatchNo());
        }
        ModelForecastConfigTb forecastConfig = saveForecastConfig(command, customer, config, "MCP发起");
        ModelForecastExecuteRequest execute = new ModelForecastExecuteRequest();
        execute.setForecastId(forecastConfig.getId());
        execute.setForecastStartDate(forecastConfig.getForecastStartDate());
        execute.setForecastHorizon(forecastConfig.getForecastHorizon());
        execute.setForecastFrequency(forecastConfig.getForecastFrequency());
        var response = forecastService.submit(execute);
        return Map.of("customerCode", customer.getCustomerCode(), "customerName", customer.getCustomerName(), "status", "EXECUTING", "trainBatchNo", trained.getBatchNo(),
                "forecastBatchNo", response.getForecastBatchNo());
    }

    private ModelForecastConfigTb saveForecastConfig(ForecastCommand command, BaseCustomerTb customer, ModelTrainConfigTb trainConfig, String remark) {
        ModelConfigTb model = trainConfig.getModelId() == null ? null : modelConfigMapper.selectById(trainConfig.getModelId());
        String frequency = forecastFrequency(command.forecastFrequency(), trainConfig.getTimeGranularity(), command.agentCode());
        int maximumHorizon = maximumHorizon(model, command.agentCode());
        String startDate = forecastStartDate(command.forecastStartDate(), customer.getCustomerCode());
        int horizon = forecastHorizon(command.forecastHorizon(), command.forecastEndDate(), startDate, frequency, maximumHorizon, command.agentCode());
        ModelForecastConfigTb existing = forecastConfigMapper.selectOne(Wrappers.<ModelForecastConfigTb>lambdaQuery().eq(ModelForecastConfigTb::getTrainConfigId, trainConfig.getId())
                .eq(ModelForecastConfigTb::getCustomerCode, customer.getCustomerCode()).eq(ModelForecastConfigTb::getEnabled, 1).orderByDesc(ModelForecastConfigTb::getUpdatedAt).last("limit 1"));
        ModelForecastConfigSaveRequest request = new ModelForecastConfigSaveRequest();
        request.setId(existing == null ? null : existing.getId());
        request.setForecastName(customer.getCustomerName() + "未来" + horizon + "天预测");
        request.setForecastStartDate(startDate);
        request.setForecastHorizon(horizon);
        request.setForecastFrequency(frequency);
        request.setTrainConfigCode(trainConfig.getTrainCode());
        request.setCustomerCode(customer.getCustomerCode());
        request.setEnabled(1);
        request.setRemark(remark);
        return forecastService.saveConfig(request);
    }

    /** 根据模型训练粒度确定预测粒度；用户传值必须与当前智能体模型一致。 */
    private String forecastFrequency(String requested, String modelGranularity, String agentCode) {
        String expected = switch (TextUtils.hasText(modelGranularity) ? modelGranularity.trim().toUpperCase() : "") {
            case "MONTH", "MONTHLY" -> "MONTHLY";
            case "TENDAY" -> "TENDAY";
            case "DAY", "DAILY" -> "DAILY";
            default -> switch (agentCode) {
                case "monthly-sales" -> "MONTHLY";
                case "winter-supply" -> "TENDAY";
                default -> "DAILY";
            };
        };
        if (TextUtils.hasText(requested) && !expected.equalsIgnoreCase(requested))
            throw new BusinessException("当前智能体只支持" + expected + "预测粒度，不能使用" + requested);
        return expected;
    }

    /** 优先使用模型配置的最大预测期数，配置缺失时按智能体采用安全默认上限。 */
    private int maximumHorizon(ModelConfigTb model, String agentCode) {
        if (model != null && model.getMaxForecastHorizon() != null && model.getMaxForecastHorizon() > 0)
            return model.getMaxForecastHorizon();
        return switch (agentCode) {
            case "monthly-sales" -> 24;
            case "winter-supply" -> 18;
            default -> 90;
        };
    }

    /** 用户指定结束日期时由日期区间计算预测期数；否则使用用户周期或默认30期。 */
    private int forecastHorizon(Integer requestedHorizon, String requestedEndDate, String startDate, String frequency, int maximumHorizon, String agentCode) {
        if (!TextUtils.hasText(requestedEndDate)) {
            int horizon = requestedHorizon == null ? defaultHorizon(agentCode) : requestedHorizon;
            if (horizon <= 0 || horizon > maximumHorizon)
                throw new BusinessException("当前模型预测周期必须在1到" + maximumHorizon + "期之间");
            return horizon;
        }
        try {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(requestedEndDate);
            if (end.isBefore(start))
                throw new BusinessException("预测结束日期不能早于预测开始日期");
            long periods = switch (frequency.toUpperCase()) {
                case "MONTHLY" -> ChronoUnit.MONTHS.between(YearMonth.from(start), YearMonth.from(end)) + 1;
                case "TENDAY" -> (ChronoUnit.DAYS.between(start, end) + 10) / 10;
                default -> ChronoUnit.DAYS.between(start, end) + 1;
            };
            if (periods > maximumHorizon)
                throw new BusinessException("预测时间范围换算后不能超过当前模型允许的" + maximumHorizon + "期");
            return (int) periods;
        } catch (DateTimeParseException exception) {
            throw new BusinessException("预测日期格式必须为 yyyy-MM-dd");
        }
    }

    private int defaultHorizon(String agentCode) {
        return switch (agentCode) {
            case "monthly-sales" -> 12;
            case "winter-supply" -> 18;
            default -> 30;
        };
    }

    private List<BaseCustomerTb> customers(ForecastCommand command) {
        var query = Wrappers.<BaseCustomerTb>lambdaQuery();
        if (command.customerCodes() != null && !command.customerCodes().isEmpty())
            query.in(BaseCustomerTb::getCustomerCode, command.customerCodes());
        else {
            if (!command.bulk())
                throw new BusinessException("单客户预测必须提供customerCodes");
            if (TextUtils.hasText(command.regionCode())) query.eq(BaseCustomerTb::getRegionCode, command.regionCode());
            if (TextUtils.hasText(command.industryCode())) query.eq(BaseCustomerTb::getIndustryCode, command.industryCode());
        }
        return customerMapper.selectList(query.orderByAsc(BaseCustomerTb::getCustomerName));
    }

    private String forecastStartDate(String requested, String customerCode) {
        DataDailySalesTb latest = dailySalesMapper.selectOne(Wrappers.<DataDailySalesTb>lambdaQuery().select(DataDailySalesTb::getStatDate).eq(DataDailySalesTb::getCustomerCode, customerCode)
                .isNotNull(DataDailySalesTb::getGasSales).orderByDesc(DataDailySalesTb::getStatDate).last("limit 1"));
        String minimum = latest == null || !TextUtils.hasText(latest.getStatDate()) ? LocalDate.now().toString() : LocalDate.parse(latest.getStatDate()).plusDays(1).toString();
        if (!TextUtils.hasText(requested))
            return minimum;
        try {
            LocalDate.parse(requested);
        } catch (DateTimeParseException exception) {
            throw new BusinessException("预测日期格式必须为 yyyy-MM-dd");
        }
        if (requested.compareTo(minimum) < 0)
            throw new BusinessException("预测开始日期不能早于客户最新销量数据的下一天：" + minimum);
        return requested;
    }

    private ModelForecastRecordTb record(String batchNo) {
        if (!TextUtils.hasText(batchNo)) throw new BusinessException("预测批次号不能为空");
        ModelForecastRecordTb record = forecastRecordMapper.selectOne(Wrappers.<ModelForecastRecordTb>lambdaQuery().eq(ModelForecastRecordTb::getForecastBatchNo, batchNo).last("limit 1"));
        if (record == null) throw new BusinessException("没有找到预测批次" + batchNo);
        return record;
    }

    private void validate(ForecastCommand command) {
        if (command == null || !TextUtils.hasText(command.agentCode())) throw new BusinessException("agentCode不能为空");
        if (command.bulk() && !command.confirmed()) throw new BusinessException("批量预测必须先获得用户确认");
    }

    private String statusName(Integer status) {
        if (status == null) return "UNKNOWN";
        for (ModelForecastStatus value : ModelForecastStatus.values()) if (value.getCode() == status) return value.name();
        return "UNKNOWN";
    }

    private String safeMessage(RuntimeException exception) {
        return TextUtils.hasText(exception.getMessage()) ? exception.getMessage() : "创建预测任务失败";
    }
}
