package com.gas.forecast.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.business.dto.req.ModelConfigCreateReqDTO;
import com.gas.forecast.business.dto.req.ModelConfigDeleteReqDTO;
import com.gas.forecast.business.dto.req.ModelConfigPageReqDTO;
import com.gas.forecast.business.dto.req.ModelConfigScopeUpdateReqDTO;
import com.gas.forecast.business.dto.req.ModelConfigUpdateReqDTO;
import com.gas.forecast.business.dto.resp.ModelConfigRespDTO;
import com.gas.forecast.business.dto.resp.ModelFeatureRefRespDTO;
import com.gas.forecast.business.service.ModelConfigService;
import com.gas.forecast.business.util.PageUtils;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.core.BusinessResponseCode;
import com.gas.forecast.common.core.PageInfoDTO;
import com.gas.forecast.common.enums.ForecastAgentEnum;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.BaseCustomerTb;
import com.gas.forecast.dao.domain.BaseIndustryTb;
import com.gas.forecast.dao.domain.BaseRegionTb;
import com.gas.forecast.dao.domain.ModelConfigScopeTb;
import com.gas.forecast.dao.domain.ModelConfigTb;
import com.gas.forecast.dao.domain.ModelFeatureDefinitionTb;
import com.gas.forecast.dao.domain.ModelFeatureRef;
import com.gas.forecast.dao.domain.ModelTrainConfigTb;
import com.gas.forecast.dao.mapper.BaseCustomerTbMapper;
import com.gas.forecast.dao.mapper.BaseIndustryTbMapper;
import com.gas.forecast.dao.mapper.BaseRegionTbMapper;
import com.gas.forecast.dao.mapper.ModelConfigScopeTbMapper;
import com.gas.forecast.dao.mapper.ModelConfigTbMapper;
import com.gas.forecast.dao.mapper.ModelFeatureDefinitionTbMapper;
import com.gas.forecast.dao.mapper.ModelFeatureRefMapper;
import com.gas.forecast.dao.mapper.ModelTrainConfigTbMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ModelConfigServiceImpl implements ModelConfigService {

    private final ModelConfigTbMapper modelConfigTbMapper;
    private final ModelConfigScopeTbMapper modelConfigScopeTbMapper;
    private final BaseRegionTbMapper baseRegionTbMapper;
    private final BaseIndustryTbMapper baseIndustryTbMapper;
    private final BaseCustomerTbMapper baseCustomerTbMapper;
    private final ModelFeatureRefMapper modelFeatureRefMapper;
    private final ModelFeatureDefinitionTbMapper modelFeatureDefinitionTbMapper;
    private final ModelTrainConfigTbMapper modelTrainConfigTbMapper;

    public ModelConfigServiceImpl(ModelConfigTbMapper modelConfigTbMapper,
                                  ModelConfigScopeTbMapper modelConfigScopeTbMapper,
                                  BaseRegionTbMapper baseRegionTbMapper,
                                  BaseIndustryTbMapper baseIndustryTbMapper,
                                  BaseCustomerTbMapper baseCustomerTbMapper,
                                  ModelFeatureRefMapper modelFeatureRefMapper,
                                  ModelFeatureDefinitionTbMapper modelFeatureDefinitionTbMapper,
                                  ModelTrainConfigTbMapper modelTrainConfigTbMapper) {
        this.modelConfigTbMapper = modelConfigTbMapper;
        this.modelConfigScopeTbMapper = modelConfigScopeTbMapper;
        this.baseRegionTbMapper = baseRegionTbMapper;
        this.baseIndustryTbMapper = baseIndustryTbMapper;
        this.baseCustomerTbMapper = baseCustomerTbMapper;
        this.modelFeatureRefMapper = modelFeatureRefMapper;
        this.modelFeatureDefinitionTbMapper = modelFeatureDefinitionTbMapper;
        this.modelTrainConfigTbMapper = modelTrainConfigTbMapper;
    }

    @Override
    public PageInfoDTO<ModelConfigRespDTO> listPage(ModelConfigPageReqDTO reqDTO) {
        LambdaQueryWrapper<ModelConfigTb> query = Wrappers.lambdaQuery();
        String keyword = reqDTO.keyword();
        if (TextUtils.hasText(keyword)) {
            query.and(wrapper -> wrapper
                    .like(ModelConfigTb::getModelCode, keyword)
                    .or()
                    .like(ModelConfigTb::getModelName, keyword)
                    .or()
                    .like(ModelConfigTb::getAgentCode, keyword)
                    .or()
                    .like(ModelConfigTb::getDescription, keyword));
        }
        query.orderByDesc(ModelConfigTb::getUpdatedAt).orderByDesc(ModelConfigTb::getId);
        int page = reqDTO.page() == null ? 1 : reqDTO.page();
        int size = reqDTO.size() == null ? 10 : reqDTO.size();
        IPage<ModelConfigTb> result = modelConfigTbMapper.selectPage(PageUtils.pageRequest(page, size), query);
        List<ModelConfigTb> records = result.getRecords();
        ScopeContext scopeContext = loadScopeContext(records);
        return PageUtils.toPage(result, records.stream().map(item -> toResp(item, scopeContext)).toList());
    }

    @Override
    @Transactional
    public ModelConfigRespDTO create(ModelConfigCreateReqDTO reqDTO) {
        ForecastAgentEnum agent = validateAgent(reqDTO.agentCode(), reqDTO.sceneCode());
        ModelConfigTb modelConfig = toEntity(reqDTO);
        String modelCode = modelConfig.getModelCode().trim();
        ensureModelCodeUnique(modelCode, null);
        Date now = new Date();
        modelConfig.setId(null);
        modelConfig.setModelCode(modelCode);
        modelConfig.setCreatedAt(now);
        modelConfig.setUpdatedAt(now);
        modelConfigTbMapper.insert(modelConfig);
        saveScopes(modelConfig.getId(), modelConfig.getModelCode(), agent, reqDTO.regionCode(), reqDTO.industryCode(), reqDTO.customerCode(),
                reqDTO.regionCodes(), reqDTO.industryCodes(), reqDTO.customerCodes());
        return toResp(modelConfig, loadScopeContext(List.of(modelConfig)));
    }

    @Override
    @Transactional
    public ModelConfigRespDTO update(ModelConfigUpdateReqDTO reqDTO) {
        validateAgent(reqDTO.agentCode(), reqDTO.sceneCode());
        ModelConfigTb exists = modelConfigTbMapper.selectById(reqDTO.id());
        if (exists == null) {
            throw new BusinessException("模型配置不存在");
        }
        ModelConfigTb modelConfig = toEntity(reqDTO);
        String modelCode = TextUtils.hasText(modelConfig.getModelCode())
                ? modelConfig.getModelCode().trim()
                : exists.getModelCode();
        ensureModelCodeUnique(modelCode, reqDTO.id());
        modelConfig.setModelCode(modelCode);
        modelConfig.setUpdatedAt(new Date());
        modelConfigTbMapper.updateById(modelConfig);
        ModelConfigTb updated = modelConfigTbMapper.selectById(reqDTO.id());
        syncTrainConfigModelDisplay(updated);
        return toResp(updated, loadScopeContext(List.of(updated)));
    }

    @Override
    @Transactional
    public ModelConfigRespDTO updateScope(ModelConfigScopeUpdateReqDTO reqDTO) {
        ModelConfigTb exists = modelConfigTbMapper.selectById(reqDTO.id());
        if (exists == null) {
            throw new BusinessException("模型配置不存在");
        }
        ForecastAgentEnum agent = ForecastAgentEnum.ofAgentCode(exists.getAgentCode())
                .orElseThrow(() -> new BusinessException("智能体编码不合法"));
        saveScopes(exists.getId(), exists.getModelCode(), agent, reqDTO.regionCode(), reqDTO.industryCode(), reqDTO.customerCode(),
                reqDTO.regionCodes(), reqDTO.industryCodes(), reqDTO.customerCodes());
        saveFeatureRefs(reqDTO.id(), reqDTO.featureRefs());
        ModelConfigTb updated = modelConfigTbMapper.selectById(reqDTO.id());
        return toResp(updated, loadScopeContext(List.of(updated)));
    }

    @Override
    @Transactional
    public void delete(ModelConfigDeleteReqDTO reqDTO) {
        ModelConfigTb exists = modelConfigTbMapper.selectById(reqDTO.id());
        if (exists == null) {
            return;
        }
        long trainConfigCount = modelTrainConfigTbMapper.selectCount(Wrappers.<ModelTrainConfigTb>lambdaQuery()
                .eq(ModelTrainConfigTb::getModelId, exists.getId()));
        if (trainConfigCount > 0) {
            throw new BusinessException("模型已被训练配置使用，不能删除");
        }
        modelConfigScopeTbMapper.delete(Wrappers.<ModelConfigScopeTb>lambdaQuery()
                .eq(ModelConfigScopeTb::getModelId, exists.getId()));
        modelFeatureRefMapper.delete(Wrappers.<ModelFeatureRef>lambdaQuery()
                .eq(ModelFeatureRef::getModelId, exists.getId()));
        modelConfigTbMapper.deleteById(reqDTO.id());
    }

    private ForecastAgentEnum validateAgent(String agentCode, String sceneCode) {
        ForecastAgentEnum agent = ForecastAgentEnum.ofAgentCode(agentCode)
                .orElseThrow(() -> new BusinessException("智能体编码不合法"));
        if (!agent.getSceneCode().equals(sceneCode)) {
            throw new BusinessException("场景编码与智能体不匹配");
        }
        return agent;
    }

    private ModelConfigRespDTO toResp(ModelConfigTb modelConfig, ScopeContext scopeContext) {
        if (modelConfig == null) {
            return null;
        }
        ForecastAgentEnum agent = ForecastAgentEnum.ofAgentCode(modelConfig.getAgentCode()).orElse(null);
        List<ModelConfigScopeTb> scopes = scopeContext.scopes().getOrDefault(modelConfig.getId(), Collections.emptyList());
        List<String> regionCodes = distinctCodes(scopes.stream().map(ModelConfigScopeTb::getRegionCode).toList());
        List<String> industryCodes = distinctCodes(scopes.stream().map(ModelConfigScopeTb::getIndustryCode).toList());
        List<String> customerCodes = distinctCodes(scopes.stream().map(ModelConfigScopeTb::getCustomerCode).toList());
        String regionCode = firstOrNull(regionCodes);
        String industryCode = firstOrNull(industryCodes);
        String customerCode = firstOrNull(customerCodes);
        BaseRegionTb region = regionCode == null ? null : scopeContext.regions().get(regionCode);
        BaseIndustryTb industry = industryCode == null ? null : scopeContext.industries().get(industryCode);
        BaseCustomerTb customer = customerCode == null ? null : scopeContext.customers().get(customerCode);
        List<String> regionNames = regionCodes.stream().map(code -> {
            BaseRegionTb item = scopeContext.regions().get(code);
            return item == null ? code : item.getRegionName();
        }).toList();
        List<String> industryNames = industryCodes.stream().map(code -> {
            BaseIndustryTb item = scopeContext.industries().get(code);
            return item == null ? code : item.getIndustryName();
        }).toList();
        List<String> customerNames = customerCodes.stream().map(code -> {
            BaseCustomerTb item = scopeContext.customers().get(code);
            return item == null ? code : item.getCustomerName();
        }).toList();
        List<ModelFeatureRefRespDTO> featureRefs = scopeContext.featureRefs().getOrDefault(modelConfig.getId(), Collections.emptyList()).stream()
                .sorted((left, right) -> {
                    int leftOrder = left.getFeatureOrder() == null ? 0 : left.getFeatureOrder();
                    int rightOrder = right.getFeatureOrder() == null ? 0 : right.getFeatureOrder();
                    return leftOrder == rightOrder ? left.getId().compareTo(right.getId()) : Integer.compare(leftOrder, rightOrder);
                })
                .map(ref -> {
                    ModelFeatureDefinitionTb definition = scopeContext.features().get(ref.getFeatureId());
                    return new ModelFeatureRefRespDTO(
                            ref.getId(),
                            ref.getFeatureId(),
                            definition == null ? null : definition.getFeatureCode(),
                            definition == null ? null : definition.getFeatureName(),
                            definition == null ? null : definition.getFeatureColumn(),
                            definition == null ? null : definition.getTimeGranularity(),
                            ref.getRequiredFlag(),
                            ref.getFeatureOrder()
                    );
                })
                .toList();
        boolean allScope = scopes.isEmpty();
        return new ModelConfigRespDTO(
                modelConfig.getId(),
                modelConfig.getModelCode(),
                modelConfig.getModelName(),
                modelConfig.getModelVersion(),
                modelConfig.getAgentCode(),
                agent == null ? null : agent.getAgentName(),
                agent == null ? null : agent.getSceneCode(),
                agent == null ? null : agent.getStrategyType(),
                regionCode,
                allScope ? "全部" : region == null ? null : region.getRegionName(),
                industryCode,
                allScope && agent != ForecastAgentEnum.WINTER_SUPPLY ? "全部" : industry == null ? null : industry.getIndustryName(),
                customerCode,
                allScope && agent != ForecastAgentEnum.WINTER_SUPPLY ? "全部" : customer == null ? null : customer.getCustomerName(),
                regionCodes,
                allScope ? List.of("全部") : regionNames,
                industryCodes,
                allScope && agent != ForecastAgentEnum.WINTER_SUPPLY ? List.of("全部") : industryNames,
                customerCodes,
                allScope && agent != ForecastAgentEnum.WINTER_SUPPLY ? List.of("全部") : customerNames,
                featureRefs,
                modelConfig.getDescription(),
                modelConfig.getCreatedAt(),
                modelConfig.getUpdatedAt()
        );
    }

    private ScopeContext loadScopeContext(List<ModelConfigTb> configs) {
        if (configs == null || configs.isEmpty()) {
            return ScopeContext.empty();
        }
        List<Long> configIds = configs.stream().map(ModelConfigTb::getId).toList();
        List<ModelConfigScopeTb> scopes = modelConfigScopeTbMapper.selectList(Wrappers.<ModelConfigScopeTb>lambdaQuery()
                .in(ModelConfigScopeTb::getModelId, configIds)
                .orderByDesc(ModelConfigScopeTb::getId));
        Map<Long, List<ModelConfigScopeTb>> scopeByConfigId = scopes.stream()
                .filter(scope -> scope.getModelId() != null)
                .collect(Collectors.groupingBy(ModelConfigScopeTb::getModelId));
        List<ModelFeatureRef> featureRefs = modelFeatureRefMapper.selectList(Wrappers.<ModelFeatureRef>lambdaQuery()
                .in(ModelFeatureRef::getModelId, configIds)
                .orderByAsc(ModelFeatureRef::getFeatureOrder)
                .orderByAsc(ModelFeatureRef::getId));
        Map<Long, List<ModelFeatureRef>> featureRefsByModelId = featureRefs.stream()
                .collect(Collectors.groupingBy(ModelFeatureRef::getModelId));
        List<Long> featureIds = featureRefs.stream().map(ModelFeatureRef::getFeatureId).filter(id -> id != null).distinct().toList();
        Map<Long, ModelFeatureDefinitionTb> features = featureIds.isEmpty()
                ? Collections.emptyMap()
                : modelFeatureDefinitionTbMapper.selectBatchIds(featureIds).stream()
                .collect(Collectors.toMap(ModelFeatureDefinitionTb::getId, Function.identity(), (left, right) -> left));

        List<String> regionCodes = scopes.stream().map(ModelConfigScopeTb::getRegionCode).filter(TextUtils::hasText).distinct().toList();
        List<String> industryCodes = scopes.stream().map(ModelConfigScopeTb::getIndustryCode).filter(TextUtils::hasText).distinct().toList();
        List<String> customerCodes = scopes.stream().map(ModelConfigScopeTb::getCustomerCode).filter(TextUtils::hasText).distinct().toList();

        Map<String, BaseRegionTb> regions = regionCodes.isEmpty()
                ? Collections.emptyMap()
                : baseRegionTbMapper.selectList(Wrappers.<BaseRegionTb>lambdaQuery().in(BaseRegionTb::getRegionCode, regionCodes))
                .stream().collect(Collectors.toMap(BaseRegionTb::getRegionCode, Function.identity(), (left, right) -> left));
        Map<String, BaseIndustryTb> industries = industryCodes.isEmpty()
                ? Collections.emptyMap()
                : baseIndustryTbMapper.selectList(Wrappers.<BaseIndustryTb>lambdaQuery().in(BaseIndustryTb::getIndustryCode, industryCodes))
                .stream().collect(Collectors.toMap(BaseIndustryTb::getIndustryCode, Function.identity(), (left, right) -> left));
        Map<String, BaseCustomerTb> customers = customerCodes.isEmpty()
                ? Collections.emptyMap()
                : baseCustomerTbMapper.selectList(Wrappers.<BaseCustomerTb>lambdaQuery().in(BaseCustomerTb::getCustomerCode, customerCodes))
                .stream().collect(Collectors.toMap(BaseCustomerTb::getCustomerCode, Function.identity(), (left, right) -> left));

        return new ScopeContext(scopeByConfigId, regions, industries, customers, featureRefsByModelId, features);
    }

    private void saveScopes(Long modelId,
                            String modelCode,
                            ForecastAgentEnum agent,
                            String regionCode,
                            String industryCode,
                            String customerCode,
                            List<String> regionCodes,
                            List<String> industryCodes,
                            List<String> customerCodes) {
        modelConfigScopeTbMapper.delete(Wrappers.<ModelConfigScopeTb>lambdaQuery()
                .eq(ModelConfigScopeTb::getModelId, modelId));

        List<String> normalizedRegionCodes = normalizeCodes(regionCodes, regionCode);
        List<String> normalizedIndustryCodes = normalizeCodes(industryCodes, industryCode);
        List<String> normalizedCustomerCodes = normalizeCodes(customerCodes, customerCode);

        if (agent == ForecastAgentEnum.WINTER_SUPPLY) {
            for (String code : normalizedRegionCodes) {
                insertScope(modelId, modelCode, code, null, null);
            }
            return;
        }

        if (normalizedRegionCodes.isEmpty() && normalizedIndustryCodes.isEmpty() && normalizedCustomerCodes.isEmpty()) {
            return;
        }

        if (!normalizedCustomerCodes.isEmpty()) {
            List<BaseCustomerTb> customers = baseCustomerTbMapper.selectList(Wrappers.<BaseCustomerTb>lambdaQuery()
                    .in(BaseCustomerTb::getCustomerCode, normalizedCustomerCodes));
            Map<String, BaseCustomerTb> customerByCode = customers.stream()
                    .collect(Collectors.toMap(BaseCustomerTb::getCustomerCode, Function.identity(), (left, right) -> left));
            for (String scopedCustomerCode : normalizedCustomerCodes) {
                BaseCustomerTb customer = customerByCode.get(scopedCustomerCode);
                if (customer == null) {
                    throw new BusinessException(BusinessResponseCode.PARAM_ERROR, "客户不存在: " + scopedCustomerCode);
                }
                insertScope(modelId, modelCode, customer.getRegionCode(), customer.getIndustryCode(), customer.getCustomerCode());
            }
            return;
        }

        List<String> regionScopeCodes = normalizedRegionCodes.isEmpty() ? Collections.singletonList(null) : normalizedRegionCodes;
        List<String> industryScopeCodes = normalizedIndustryCodes.isEmpty() ? Collections.singletonList(null) : normalizedIndustryCodes;
        for (String scopedRegionCode : regionScopeCodes) {
            for (String scopedIndustryCode : industryScopeCodes) {
                insertScope(modelId, modelCode, scopedRegionCode, scopedIndustryCode, null);
            }
        }
    }

    private void insertScope(Long modelId, String modelCode, String regionCode, String industryCode, String customerCode) {
        ModelConfigScopeTb scope = new ModelConfigScopeTb();
        scope.setModelId(modelId);
        scope.setModelCode(modelCode);
        scope.setRegionCode(regionCode);
        scope.setIndustryCode(industryCode);
        scope.setCustomerCode(customerCode);
        scope.setCreatedAt(new Date());
        scope.setUpdatedAt(new Date());
        modelConfigScopeTbMapper.insert(scope);
    }

    private void saveFeatureRefs(Long modelConfigId, List<ModelConfigScopeUpdateReqDTO.FeatureRefItem> featureRefs) {
        modelFeatureRefMapper.delete(Wrappers.<ModelFeatureRef>lambdaQuery()
                .eq(ModelFeatureRef::getModelId, modelConfigId));
        if (featureRefs == null || featureRefs.isEmpty()) {
            return;
        }
        List<ModelConfigScopeUpdateReqDTO.FeatureRefItem> distinctRefs = new ArrayList<>(featureRefs.stream()
                .filter(item -> item.featureId() != null)
                .collect(Collectors.toMap(
                        ModelConfigScopeUpdateReqDTO.FeatureRefItem::featureId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ))
                .values());
        List<Long> featureIds = distinctRefs.stream().map(ModelConfigScopeUpdateReqDTO.FeatureRefItem::featureId).toList();
        if (featureIds.isEmpty()) {
            return;
        }
        long existingCount = modelFeatureDefinitionTbMapper.selectCount(Wrappers.<ModelFeatureDefinitionTb>lambdaQuery()
                .in(ModelFeatureDefinitionTb::getId, featureIds)
                .eq(ModelFeatureDefinitionTb::getEnabled, 1));
        if (existingCount != featureIds.size()) {
            throw new BusinessException(BusinessResponseCode.PARAM_ERROR, "存在未启用或不存在的特征");
        }
        int index = 1;
        for (ModelConfigScopeUpdateReqDTO.FeatureRefItem item : distinctRefs) {
            ModelFeatureRef ref = new ModelFeatureRef();
            ref.setModelId(modelConfigId);
            ref.setFeatureId(item.featureId());
            ref.setRequiredFlag(item.requiredFlag() == null ? 0 : item.requiredFlag());
            ref.setFeatureOrder(item.featureOrder() == null ? index : item.featureOrder());
            ref.setCreatedAt(new Date());
            ref.setCreatedBy(0L);
            modelFeatureRefMapper.insert(ref);
            index++;
        }
    }

    private List<String> normalizeCodes(List<String> values, String fallback) {
        List<String> source = values == null ? Collections.emptyList() : values;
        List<String> codes = source.stream()
                .filter(TextUtils::hasText)
                .filter(value -> !"ALL".equals(value))
                .distinct()
                .toList();
        if (!codes.isEmpty()) {
            return codes;
        }
        if (TextUtils.hasText(fallback) && !"ALL".equals(fallback)) {
            return List.of(fallback);
        }
        return Collections.emptyList();
    }

    private List<String> distinctCodes(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return values.stream().filter(TextUtils::hasText).distinct().toList();
    }

    private String firstOrNull(List<String> values) {
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private void ensureModelCodeUnique(String modelCode, Long excludeId) {
        if (!TextUtils.hasText(modelCode)) {
            throw new BusinessException("模型编码不能为空");
        }
        LambdaQueryWrapper<ModelConfigTb> query = Wrappers.<ModelConfigTb>lambdaQuery()
                .eq(ModelConfigTb::getModelCode, modelCode);
        if (excludeId != null) {
            query.ne(ModelConfigTb::getId, excludeId);
        }
        if (modelConfigTbMapper.selectCount(query) > 0) {
            throw new BusinessException("模型编码已存在");
        }
    }

    private void syncTrainConfigModelDisplay(ModelConfigTb modelConfig) {
        if (modelConfig == null || modelConfig.getId() == null) {
            return;
        }
        List<ModelTrainConfigTb> trainConfigs = modelTrainConfigTbMapper.selectList(Wrappers.<ModelTrainConfigTb>lambdaQuery()
                .eq(ModelTrainConfigTb::getModelId, modelConfig.getId()));
        for (ModelTrainConfigTb trainConfig : trainConfigs) {
            trainConfig.setModelCode(modelConfig.getModelCode());
            trainConfig.setModelName(modelConfig.getModelName());
            trainConfig.setUpdatedAt(new Date());
            modelTrainConfigTbMapper.updateById(trainConfig);
        }
    }

    private record ScopeContext(
            Map<Long, List<ModelConfigScopeTb>> scopes,
            Map<String, BaseRegionTb> regions,
            Map<String, BaseIndustryTb> industries,
            Map<String, BaseCustomerTb> customers,
            Map<Long, List<ModelFeatureRef>> featureRefs,
            Map<Long, ModelFeatureDefinitionTb> features
    ) {
        private static ScopeContext empty() {
            return new ScopeContext(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
        }
    }

    private ModelConfigTb toEntity(ModelConfigCreateReqDTO reqDTO) {
        ModelConfigTb modelConfig = new ModelConfigTb();
        modelConfig.setModelCode(reqDTO.configCode());
        modelConfig.setModelName(reqDTO.configName());
        modelConfig.setModelVersion(TextUtils.hasText(reqDTO.modelVersion()) ? reqDTO.modelVersion() : "V1.0");
        modelConfig.setAgentCode(reqDTO.agentCode());
        modelConfig.setDescription(reqDTO.description());
        return modelConfig;
    }

    private ModelConfigTb toEntity(ModelConfigUpdateReqDTO reqDTO) {
        ModelConfigTb modelConfig = new ModelConfigTb();
        modelConfig.setId(reqDTO.id());
        modelConfig.setModelCode(reqDTO.configCode());
        modelConfig.setModelName(reqDTO.configName());
        modelConfig.setModelVersion(TextUtils.hasText(reqDTO.modelVersion()) ? reqDTO.modelVersion() : "V1.0");
        modelConfig.setAgentCode(reqDTO.agentCode());
        modelConfig.setDescription(reqDTO.description());
        return modelConfig;
    }
}
