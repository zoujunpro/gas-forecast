package com.gas.forecast.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.business.dto.request.AutoTrainRequest;
import com.gas.forecast.business.dto.response.AutoTrainResponse;
import com.gas.forecast.business.enums.BaseCodeType;
import com.gas.forecast.business.enums.ModelTrainMode;
import com.gas.forecast.business.enums.ModelTrainStatus;
import com.gas.forecast.business.service.AutoTrainService;
import com.gas.forecast.business.service.BaseCodeGenerateService;
import com.gas.forecast.business.service.ModelTrainQueueService;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.enums.ForecastAgentEnum;
import com.gas.forecast.common.security.context.SecurityContextHolder;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.AgentStrategyConfigTb;
import com.gas.forecast.dao.domain.BaseCustomerTb;
import com.gas.forecast.dao.domain.BaseIndustryTb;
import com.gas.forecast.dao.domain.BaseRegionTb;
import com.gas.forecast.dao.domain.ModelConfigScopeTb;
import com.gas.forecast.dao.domain.ModelConfigTb;
import com.gas.forecast.dao.domain.ModelTrainConfigTb;
import com.gas.forecast.dao.domain.ModelTrainRecordTb;
import com.gas.forecast.dao.mapper.AgentStrategyConfigTbMapper;
import com.gas.forecast.dao.mapper.BaseCustomerTbMapper;
import com.gas.forecast.dao.mapper.BaseIndustryTbMapper;
import com.gas.forecast.dao.mapper.BaseRegionTbMapper;
import com.gas.forecast.dao.mapper.ModelConfigScopeTbMapper;
import com.gas.forecast.dao.mapper.ModelConfigTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainConfigTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainRecordTbMapper;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AutoTrainServiceImpl implements AutoTrainService {
    private static final Set<String> TRIGGER_SOURCES = Set.of("MANUAL", "AI", "SCHEDULE", "DATA_EVENT");

    private final AgentStrategyConfigTbMapper strategyMapper;
    private final BaseCustomerTbMapper customerMapper;
    private final BaseRegionTbMapper regionMapper;
    private final BaseIndustryTbMapper industryMapper;
    private final ModelConfigTbMapper modelMapper;
    private final ModelConfigScopeTbMapper scopeMapper;
    private final ModelTrainConfigTbMapper trainConfigMapper;
    private final ModelTrainRecordTbMapper trainRecordMapper;
    private final BaseCodeGenerateService codeGenerateService;
    private final ModelTrainQueueService trainQueueService;

    @Override
    @Transactional
    public AutoTrainResponse execute(AutoTrainRequest request) {
        String triggerSource = upperOrDefault(request.getTriggerSource(), "MANUAL");
        if (!TRIGGER_SOURCES.contains(triggerSource)) {
            throw new BusinessException("不支持的训练触发来源：" + triggerSource);
        }
        if (request.getTrainConfigId() != null) {
            return executeConfigured(request, triggerSource);
        }
        String agentCode = trim(request.getAgentCode());
        if (!TextUtils.hasText(agentCode))
            throw new BusinessException("智能体编码不能为空");
        ForecastAgentEnum agent = ForecastAgentEnum.ofAgentCode(agentCode).orElseThrow(() -> new BusinessException("不支持的智能体：" + agentCode));

        AgentStrategyConfigTb strategy = strategyMapper.selectOne(Wrappers.<AgentStrategyConfigTb>lambdaQuery().eq(AgentStrategyConfigTb::getAgentCode, agentCode).last("limit 1"));
        if (strategy == null || !Integer.valueOf(1).equals(strategy.getEnabled())) {
            throw new BusinessException("智能体策略不存在或未启用");
        }
        if (("SCHEDULE".equals(triggerSource) || "DATA_EVENT".equals(triggerSource)) && !Integer.valueOf(1).equals(strategy.getAutoTrainEnabled())) {
            throw new BusinessException("该智能体未开启自动训练");
        }

        String trainScopeType = "short-term".equals(agentCode) ? "CUSTOMER" : strategy.getTrainScopeType();
        Target target = resolveTarget(request);
        validateRequiredScope(trainScopeType, target);
        ModelConfigTb model = resolveModel(agentCode, target);
        TrainingRange range = resolveDateRange(request, model);
        target = normalizeTrainingTarget(trainScopeType, target);
        int recentPeriods = resolveRecommendedPeriods(model, range);
        ModelTrainConfigTb trainConfig = findOrCreateConfig(agent, model, target, range, recentPeriods, triggerSource);

        if (!Boolean.TRUE.equals(request.getForceTrain())) {
            ModelTrainRecordTb active = trainRecordMapper.selectOne(Wrappers.<ModelTrainRecordTb>lambdaQuery().eq(ModelTrainRecordTb::getTrainConfigId, trainConfig.getId())
                    .in(ModelTrainRecordTb::getStatus, ModelTrainStatus.PENDING.getCode(), ModelTrainStatus.RUNNING.getCode()).orderByDesc(ModelTrainRecordTb::getId).last("limit 1"));
            if (active != null) {
                throw new BusinessException("相同模型、范围和时间条件已有训练任务正在执行，批次号：" + active.getBatchNo());
            }
        }

        ModelTrainRecordTb task = trainQueueService.enqueue(trainConfig, triggerSource, Boolean.TRUE.equals(request.getForceTrain()), null);
        return new AutoTrainResponse(agentCode, triggerSource, model.getId(), model.getModelCode(), trainConfig.getId(), target.regionCode(), target.industryCode(), target.customerCode(),
                range.mode().name(), range.startDate(), range.endDate(), range.mode() == ModelTrainMode.RECENT ? recentPeriods : null, task.getBatchNo(),
                task.getStatus());
    }

    private AutoTrainResponse executeConfigured(AutoTrainRequest request, String triggerSource) {
        ModelTrainConfigTb config = trainConfigMapper.selectById(request.getTrainConfigId());
        if (config == null)
            throw new BusinessException("训练配置不存在");
        if (!Integer.valueOf(1).equals(config.getEnabled()))
            throw new BusinessException("训练配置未启用");
        if ("short-term".equals(config.getAgentCode()) && (!TextUtils.hasText(config.getRegionCode()) || !TextUtils.hasText(config.getIndustryCode())
                || !TextUtils.hasText(config.getCustomerCode()) || "ALL".equalsIgnoreCase(config.getCustomerCode())))
            throw new BusinessException("短期预测训练配置必须同时指定地区、行业和具体客户");
        ModelConfigTb model = modelMapper.selectById(config.getModelId());
        if (model == null)
            throw new BusinessException("所属模型不存在");
        if (!TextUtils.hasText(request.getRetryBatchNo()) && !Boolean.TRUE.equals(request.getForceTrain())) {
            ModelTrainRecordTb active = trainRecordMapper.selectOne(Wrappers.<ModelTrainRecordTb>lambdaQuery().eq(ModelTrainRecordTb::getTrainConfigId, config.getId())
                    .in(ModelTrainRecordTb::getStatus, ModelTrainStatus.PENDING.getCode(), ModelTrainStatus.RUNNING.getCode()).orderByDesc(ModelTrainRecordTb::getId).last("limit 1"));
            if (active != null)
                throw new BusinessException("该训练配置已有任务正在执行，批次号：" + active.getBatchNo());
        }
        ModelTrainRecordTb task = trainQueueService.enqueue(config, triggerSource, Boolean.TRUE.equals(request.getForceTrain()), trim(request.getRetryBatchNo()));
        return new AutoTrainResponse(config.getAgentCode(), triggerSource, model.getId(), model.getModelCode(), config.getId(), config.getRegionCode(), config.getIndustryCode(),
                config.getCustomerCode(), config.getTrainMode(), config.getTrainStartDate(), config.getTrainEndDate(), config.getRecentPeriods(), task.getBatchNo(), task.getStatus());
    }

    private Target resolveTarget(AutoTrainRequest request) {
        String customerCode = trim(request.getCustomerCode());
        if (TextUtils.hasText(customerCode)) {
            BaseCustomerTb customer = customerMapper.selectOne(Wrappers.<BaseCustomerTb>lambdaQuery().eq(BaseCustomerTb::getCustomerCode, customerCode).last("limit 1"));
            if (customer == null)
                throw new BusinessException("训练客户不存在：" + customerCode);
            return new Target(customer.getRegionCode(), customer.getRegionName(), customer.getIndustryCode(), customer.getIndustryName(), customerCode, customer.getCustomerName());
        }
        String regionCode = trim(request.getRegionCode());
        String industryCode = trim(request.getIndustryCode());
        BaseRegionTb region = TextUtils.hasText(regionCode) ? regionMapper.selectOne(Wrappers.<BaseRegionTb>lambdaQuery().eq(BaseRegionTb::getRegionCode, regionCode).last("limit 1")) : null;
        BaseIndustryTb industry = TextUtils.hasText(industryCode)
                ? industryMapper.selectOne(Wrappers.<BaseIndustryTb>lambdaQuery().eq(BaseIndustryTb::getIndustryCode, industryCode).last("limit 1"))
                : null;
        if (TextUtils.hasText(regionCode) && region == null)
            throw new BusinessException("训练地区不存在：" + regionCode);
        if (TextUtils.hasText(industryCode) && industry == null)
            throw new BusinessException("训练行业不存在：" + industryCode);
        return new Target(regionCode, region == null ? null : region.getRegionName(), industryCode, industry == null ? null : industry.getIndustryName(), null, null);
    }

    private void validateRequiredScope(String scopeType, Target target) {
        switch (scopeType) {
            case "CUSTOMER" -> require(target.customerCode(), "该智能体训练必须指定客户");
            case "REGION" -> require(target.regionCode(), "该智能体训练必须指定地区");
            case "INDUSTRY" -> require(target.industryCode(), "该智能体训练必须指定行业");
            case "REGION_INDUSTRY" -> {
                require(target.regionCode(), "该智能体训练必须指定地区");
                require(target.industryCode(), "该智能体训练必须指定行业");
            }
            default -> throw new BusinessException("智能体训练主体配置不正确：" + scopeType);
        }
    }

    private Target normalizeTrainingTarget(String scopeType, Target target) {
        return switch (scopeType) {
            case "REGION" -> new Target(target.regionCode(), target.regionName(), null, null, null, null);
            case "INDUSTRY", "REGION_INDUSTRY" -> new Target(target.regionCode(), target.regionName(), target.industryCode(), target.industryName(), null, null);
            default -> target;
        };
    }

    private TrainingRange resolveDateRange(AutoTrainRequest request, ModelConfigTb model) {
        String start = trim(request.getTrainStartDate());
        String end = trim(request.getTrainEndDate());
        if (TextUtils.hasText(start) != TextUtils.hasText(end))
            throw new BusinessException("自定义训练开始日期和结束日期必须同时填写");
        ModelTrainMode mode;
        try {
            mode = TextUtils.hasText(request.getTrainMode()) ? ModelTrainMode.valueOf(request.getTrainMode().trim().toUpperCase()) : null;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("训练方式只支持 ALL、RECENT、RANGE");
        }
        if (TextUtils.hasText(start))
            mode = ModelTrainMode.RANGE;
        if (mode == null) {
            String defaultMode = trim(model.getDefaultTrainingRangeMode());
            mode = "ALL".equalsIgnoreCase(defaultMode) ? ModelTrainMode.ALL : ModelTrainMode.RECENT;
            if ("CUSTOM".equalsIgnoreCase(defaultMode) && request.getRecentPeriods() == null)
                request.setRecentPeriods(model.getDefaultTrainingPeriods() == null ? model.getRecommendedTrainingPeriods() : model.getDefaultTrainingPeriods());
        }
        if (mode == ModelTrainMode.RANGE && !TextUtils.hasText(start))
            throw new BusinessException("RANGE 训练方式必须填写训练开始日期和结束日期");
        if (mode != ModelTrainMode.RANGE && TextUtils.hasText(start))
            throw new BusinessException("只有 RANGE 训练方式可以填写训练开始日期和结束日期");
        if (mode == ModelTrainMode.RECENT && request.getRecentPeriods() != null && request.getRecentPeriods() <= 0)
            throw new BusinessException("最近训练周期数必须大于0");
        if (!TextUtils.hasText(start))
            return new TrainingRange(mode, null, null, request.getRecentPeriods());
        try {
            LocalDate startDate = LocalDate.parse(start);
            LocalDate endDate = LocalDate.parse(end);
            if (startDate.isAfter(endDate))
                throw new BusinessException("训练开始日期不能晚于结束日期");
        } catch (DateTimeParseException exception) {
            throw new BusinessException("训练日期格式必须为 yyyy-MM-dd");
        }
        return new TrainingRange(ModelTrainMode.RANGE, start, end, null);
    }

    private ModelConfigTb resolveModel(String agentCode, Target target) {
        List<ModelConfigTb> models = modelMapper.selectList(Wrappers.<ModelConfigTb>lambdaQuery().eq(ModelConfigTb::getAgentCode, agentCode));
        return models.stream().map(model -> new ModelMatch(model, scopeScore(model.getId(), target))).filter(match -> match.score() >= 0)
                .max(Comparator.comparingInt(ModelMatch::score).thenComparing(match -> match.model().getId())).map(ModelMatch::model).orElseThrow(() -> new BusinessException("没有找到支持当前业务范围的模型"));
    }

    private int scopeScore(Long modelId, Target target) {
        List<ModelConfigScopeTb> scopes = scopeMapper.selectList(Wrappers.<ModelConfigScopeTb>lambdaQuery().eq(ModelConfigScopeTb::getModelId, modelId));
        if (scopes.isEmpty())
            return 0;
        int exactCustomerScore = scopes.stream()
                .filter(scope -> matches(scope.getRegionCode(), target.regionCode()) && matches(scope.getIndustryCode(), target.industryCode())
                        && matches(scope.getCustomerCode(), target.customerCode()))
                .mapToInt(scope -> (TextUtils.hasText(scope.getCustomerCode()) ? 100 : 0) + (TextUtils.hasText(scope.getRegionCode()) ? 20 : 0) + (TextUtils.hasText(scope.getIndustryCode()) ? 10 : 0))
                .max().orElse(-1);
        if (exactCustomerScore >= 0)
            return exactCustomerScore;

        // 客户级范围用于精确优先级，但不应阻止同地区、同行业的新客户使用模型。
        // 当客户基础信息已经确定地区和行业时，按模型的业务范围兜底匹配。
        if (!TextUtils.hasText(target.industryCode()))
            return -1;
        return scopes.stream()
                .filter(scope -> TextUtils.hasText(scope.getIndustryCode()) && matches(scope.getRegionCode(), target.regionCode()) && scope.getIndustryCode().equals(target.industryCode()))
                .mapToInt(scope -> (TextUtils.hasText(scope.getRegionCode()) ? 2 : 0) + (TextUtils.hasText(scope.getIndustryCode()) ? 1 : 0)).max().orElse(-1);
    }

    private boolean matches(String configured, String actual) {
        return !TextUtils.hasText(configured) || configured.equals(actual);
    }

    private int resolveRecommendedPeriods(ModelConfigTb model, TrainingRange range) {
        if (range.mode() != ModelTrainMode.RECENT)
            return 0;
        if (range.recentPeriods() != null)
            return range.recentPeriods();
        Integer periods = model.getRecommendedTrainingPeriods();
        if (periods == null || periods <= 0)
            periods = model.getMinimumTrainingRows();
        if (periods == null || periods <= 0)
            throw new BusinessException("模型未配置推荐训练周期数");
        return periods;
    }

    private ModelTrainConfigTb findOrCreateConfig(ForecastAgentEnum agent, ModelConfigTb model, Target target, TrainingRange range, int recentPeriods, String triggerSource) {
        LambdaQueryWrapper<ModelTrainConfigTb> query = Wrappers.<ModelTrainConfigTb>lambdaQuery().eq(ModelTrainConfigTb::getAgentCode, agent.getAgentCode())
                .eq(ModelTrainConfigTb::getModelId, model.getId()).eq(ModelTrainConfigTb::getTimeGranularity, model.getTimeGranularity());
        eqOrNull(query, ModelTrainConfigTb::getRegionCode, target.regionCode());
        eqOrNull(query, ModelTrainConfigTb::getIndustryCode, target.industryCode());
        eqOrNull(query, ModelTrainConfigTb::getCustomerCode, target.customerCode());
        ModelTrainConfigTb existing = trainConfigMapper.selectOne(query.orderByDesc(ModelTrainConfigTb::getId).last("limit 1"));
        if (existing != null) {
            existing.setTrainMode(range.mode().name());
            existing.setTrainStartDate(range.mode() == ModelTrainMode.RANGE ? range.startDate() : null);
            existing.setTrainEndDate(range.mode() == ModelTrainMode.RANGE ? range.endDate() : null);
            existing.setRecentPeriods(range.mode() == ModelTrainMode.RECENT ? recentPeriods : null);
            existing.setEnabled(1);
            existing.setRemark("由统一训练入口更新，触发来源：" + triggerSource);
            existing.setUpdatedAt(new Date());
            trainConfigMapper.updateById(existing);
            return existing;
        }

        Date now = new Date();
        ModelTrainConfigTb config = new ModelTrainConfigTb();
        config.setTrainCode(codeGenerateService.nextCode(BaseCodeType.MODEL_TRAIN_CONFIG));
        config.setTrainName(agent.getAgentName() + "-自动训练-" + scopeLabel(target));
        config.setAgentCode(agent.getAgentCode());
        config.setModelId(model.getId());
        config.setModelCode(model.getModelCode());
        config.setModelName(model.getModelName());
        config.setRegionCode(target.regionCode());
        config.setRegionName(target.regionName());
        config.setIndustryCode(target.industryCode());
        config.setIndustryName(target.industryName());
        config.setCustomerCode(target.customerCode());
        config.setCustomerName(target.customerName());
        config.setTrainMode(range.mode().name());
        config.setTrainStartDate(range.startDate());
        config.setTrainEndDate(range.endDate());
        config.setRecentPeriods(range.mode() == ModelTrainMode.RECENT ? recentPeriods : null);
        config.setTimeGranularity(model.getTimeGranularity());
        config.setEnabled(1);
        config.setRemark("由统一训练入口自动生成，触发来源：" + triggerSource);
        config.setCreatedBy(String.valueOf(SecurityContextHolder.getUserId()));
        config.setCreatedByName(SecurityContextHolder.getUserName());
        config.setCreatedAt(now);
        config.setUpdatedAt(now);
        trainConfigMapper.insert(config);
        return config;
    }

    private String scopeLabel(Target target) {
        if (TextUtils.hasText(target.customerName()))
            return target.customerName();
        if (TextUtils.hasText(target.regionName()) && TextUtils.hasText(target.industryName()))
            return target.regionName() + "-" + target.industryName();
        if (TextUtils.hasText(target.regionName()))
            return target.regionName();
        if (TextUtils.hasText(target.industryName()))
            return target.industryName();
        return "全部范围";
    }

    private <T> void eqOrNull(LambdaQueryWrapper<T> query, com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, String> column, String value) {
        if (TextUtils.hasText(value))
            query.eq(column, value);
        else
            query.isNull(column);
    }

    private void require(String value, String message) {
        if (!TextUtils.hasText(value))
            throw new BusinessException(message);
    }
    private String trim(String value) {
        return value == null ? null : value.trim();
    }
    private String upperOrDefault(String value, String fallback) {
        return TextUtils.hasText(value) ? value.trim().toUpperCase() : fallback;
    }

    private record Target(String regionCode, String regionName, String industryCode, String industryName, String customerCode, String customerName) {
    }
    private record TrainingRange(ModelTrainMode mode, String startDate, String endDate, Integer recentPeriods) {
    }
    private record ModelMatch(ModelConfigTb model, int score) {
    }
}
