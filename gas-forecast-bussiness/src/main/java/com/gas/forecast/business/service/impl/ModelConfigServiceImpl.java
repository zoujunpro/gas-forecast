package com.gas.forecast.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.business.dto.req.ModelConfigCreateReqDTO;
import com.gas.forecast.business.dto.req.ModelConfigDeleteReqDTO;
import com.gas.forecast.business.dto.req.ModelConfigPageReqDTO;
import com.gas.forecast.business.dto.req.ModelConfigUpdateReqDTO;
import com.gas.forecast.business.dto.resp.ModelConfigRespDTO;
import com.gas.forecast.business.enums.BaseCodeType;
import com.gas.forecast.business.service.BaseCodeGenerateService;
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
import com.gas.forecast.dao.mapper.BaseCustomerTbMapper;
import com.gas.forecast.dao.mapper.BaseIndustryTbMapper;
import com.gas.forecast.dao.mapper.BaseRegionTbMapper;
import com.gas.forecast.dao.mapper.ModelConfigScopeTbMapper;
import com.gas.forecast.dao.mapper.ModelConfigTbMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Collections;
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
    private final BaseCodeGenerateService baseCodeGenerateService;

    public ModelConfigServiceImpl(ModelConfigTbMapper modelConfigTbMapper,
                                  ModelConfigScopeTbMapper modelConfigScopeTbMapper,
                                  BaseRegionTbMapper baseRegionTbMapper,
                                  BaseIndustryTbMapper baseIndustryTbMapper,
                                  BaseCustomerTbMapper baseCustomerTbMapper,
                                  BaseCodeGenerateService baseCodeGenerateService) {
        this.modelConfigTbMapper = modelConfigTbMapper;
        this.modelConfigScopeTbMapper = modelConfigScopeTbMapper;
        this.baseRegionTbMapper = baseRegionTbMapper;
        this.baseIndustryTbMapper = baseIndustryTbMapper;
        this.baseCustomerTbMapper = baseCustomerTbMapper;
        this.baseCodeGenerateService = baseCodeGenerateService;
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
        Date now = new Date();
        modelConfig.setId(null);
        modelConfig.setModelCode(baseCodeGenerateService.nextCode(BaseCodeType.MODEL_CONFIG));
        modelConfig.setCreatedAt(now);
        modelConfig.setUpdatedAt(now);
        modelConfigTbMapper.insert(modelConfig);
        saveScopes(modelConfig.getId(), agent, reqDTO.regionCode(), reqDTO.industryCode(), reqDTO.customerCode(),
                reqDTO.regionCodes(), reqDTO.industryCodes(), reqDTO.customerCodes());
        return toResp(modelConfig, loadScopeContext(List.of(modelConfig)));
    }

    @Override
    @Transactional
    public ModelConfigRespDTO update(ModelConfigUpdateReqDTO reqDTO) {
        ForecastAgentEnum agent = validateAgent(reqDTO.agentCode(), reqDTO.sceneCode());
        ModelConfigTb exists = modelConfigTbMapper.selectById(reqDTO.id());
        if (exists == null) {
            throw new BusinessException("模型配置不存在");
        }
        ModelConfigTb modelConfig = toEntity(reqDTO);
        modelConfig.setModelCode(exists.getModelCode());
        modelConfig.setUpdatedAt(new Date());
        modelConfigTbMapper.updateById(modelConfig);
        saveScopes(reqDTO.id(), agent, reqDTO.regionCode(), reqDTO.industryCode(), reqDTO.customerCode(),
                reqDTO.regionCodes(), reqDTO.industryCodes(), reqDTO.customerCodes());
        ModelConfigTb updated = modelConfigTbMapper.selectById(reqDTO.id());
        return toResp(updated, loadScopeContext(List.of(updated)));
    }

    @Override
    @Transactional
    public void delete(ModelConfigDeleteReqDTO reqDTO) {
        modelConfigScopeTbMapper.delete(Wrappers.<ModelConfigScopeTb>lambdaQuery()
                .eq(ModelConfigScopeTb::getModelCode, reqDTO.id()));
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
        boolean allScope = scopes.isEmpty();
        return new ModelConfigRespDTO(
                modelConfig.getId(),
                modelConfig.getModelCode(),
                modelConfig.getModelName(),
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
                .in(ModelConfigScopeTb::getModelCode, configIds)
                .orderByDesc(ModelConfigScopeTb::getId));
        Map<Long, List<ModelConfigScopeTb>> scopeByConfigId = scopes.stream()
                .collect(Collectors.groupingBy(ModelConfigScopeTb::getModelCode));

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

        return new ScopeContext(scopeByConfigId, regions, industries, customers);
    }

    private void saveScopes(Long modelConfigId,
                            ForecastAgentEnum agent,
                            String regionCode,
                            String industryCode,
                            String customerCode,
                            List<String> regionCodes,
                            List<String> industryCodes,
                            List<String> customerCodes) {
        modelConfigScopeTbMapper.delete(Wrappers.<ModelConfigScopeTb>lambdaQuery()
                .eq(ModelConfigScopeTb::getModelCode, modelConfigId));

        List<String> normalizedRegionCodes = normalizeCodes(regionCodes, regionCode);
        List<String> normalizedIndustryCodes = normalizeCodes(industryCodes, industryCode);
        List<String> normalizedCustomerCodes = normalizeCodes(customerCodes, customerCode);

        if (agent == ForecastAgentEnum.WINTER_SUPPLY) {
            for (String code : normalizedRegionCodes) {
                insertScope(modelConfigId, code, null, null);
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
                insertScope(modelConfigId, customer.getRegionCode(), customer.getIndustryCode(), customer.getCustomerCode());
            }
            return;
        }

        List<String> regionScopeCodes = normalizedRegionCodes.isEmpty() ? List.of((String) null) : normalizedRegionCodes;
        List<String> industryScopeCodes = normalizedIndustryCodes.isEmpty() ? List.of((String) null) : normalizedIndustryCodes;
        for (String scopedRegionCode : regionScopeCodes) {
            for (String scopedIndustryCode : industryScopeCodes) {
                insertScope(modelConfigId, scopedRegionCode, scopedIndustryCode, null);
            }
        }
    }

    private void insertScope(Long modelConfigId, String regionCode, String industryCode, String customerCode) {
        ModelConfigScopeTb scope = new ModelConfigScopeTb();
        scope.setModelCode(modelConfigId);
        scope.setRegionCode(regionCode);
        scope.setIndustryCode(industryCode);
        scope.setCustomerCode(customerCode);
        scope.setCreatedAt(new Date());
        scope.setUpdatedAt(new Date());
        modelConfigScopeTbMapper.insert(scope);
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

    private record ScopeContext(
            Map<Long, List<ModelConfigScopeTb>> scopes,
            Map<String, BaseRegionTb> regions,
            Map<String, BaseIndustryTb> industries,
            Map<String, BaseCustomerTb> customers
    ) {
        private static ScopeContext empty() {
            return new ScopeContext(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
        }
    }

    private ModelConfigTb toEntity(ModelConfigCreateReqDTO reqDTO) {
        ModelConfigTb modelConfig = new ModelConfigTb();
        modelConfig.setModelCode(reqDTO.configCode());
        modelConfig.setModelName(reqDTO.configName());
        modelConfig.setAgentCode(reqDTO.agentCode());
        modelConfig.setDescription(reqDTO.description());
        return modelConfig;
    }

    private ModelConfigTb toEntity(ModelConfigUpdateReqDTO reqDTO) {
        ModelConfigTb modelConfig = new ModelConfigTb();
        modelConfig.setId(reqDTO.id());
        modelConfig.setModelCode(reqDTO.configCode());
        modelConfig.setModelName(reqDTO.configName());
        modelConfig.setAgentCode(reqDTO.agentCode());
        modelConfig.setDescription(reqDTO.description());
        return modelConfig;
    }
}
