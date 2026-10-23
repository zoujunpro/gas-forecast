package com.gas.forecast.business.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.business.service.BusinessEntityResolutionService;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.BaseCustomerTb;
import com.gas.forecast.dao.domain.BaseIndustryTb;
import com.gas.forecast.dao.domain.BaseRegionTb;
import com.gas.forecast.dao.mapper.BaseCustomerTbMapper;
import com.gas.forecast.dao.mapper.BaseIndustryTbMapper;
import com.gas.forecast.dao.mapper.BaseRegionTbMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 复用基础实体表完成精确、模糊和业务别名解析。 */
@Service
@RequiredArgsConstructor
public class BusinessEntityResolutionServiceImpl implements BusinessEntityResolutionService {
    private static final Map<String, String> INDUSTRY_ALIASES = Map.ofEntries(Map.entry("城市燃气", "城燃"), Map.entry("城市燃气行业", "城燃"), Map.entry("城镇燃气", "城燃"),
            Map.entry("城镇燃气行业", "城燃"), Map.entry("电力", "发电"), Map.entry("发电行业", "发电"), Map.entry("化工行业", "化工"));
    private final BaseCustomerTbMapper customerMapper;
    private final BaseRegionTbMapper regionMapper;
    private final BaseIndustryTbMapper industryMapper;

    @Override
    public ResolutionResult resolveCustomer(EntityQuery query) {
        int limit = limit(query.limit());
        var wrapper = Wrappers.<BaseCustomerTb>lambdaQuery();
        if (TextUtils.hasText(query.customerCode()))
            wrapper.eq(BaseCustomerTb::getCustomerCode, query.customerCode());
        if (TextUtils.hasText(query.customerName()))
            wrapper.and(q -> q.eq(BaseCustomerTb::getCustomerName, query.customerName()).or().like(BaseCustomerTb::getCustomerName, query.customerName()));
        if (TextUtils.hasText(query.regionCode()))
            wrapper.eq(BaseCustomerTb::getRegionCode, query.regionCode());
        if (TextUtils.hasText(query.regionName()))
            wrapper.eq(BaseCustomerTb::getRegionName, normalizeRegion(query.regionName()));
        if (TextUtils.hasText(query.industryCode()))
            wrapper.eq(BaseCustomerTb::getIndustryCode, query.industryCode());
        if (TextUtils.hasText(query.industryName()))
            wrapper.eq(BaseCustomerTb::getIndustryName, normalizeIndustry(query.industryName()));
        List<EntityCandidate> rows = customerMapper.selectList(wrapper.orderByAsc(BaseCustomerTb::getCustomerName).last("limit " + limit)).stream().map(this::customer).toList();
        return result("客户", rows);
    }

    @Override
    public ResolutionResult resolveRegion(EntityQuery query) {
        String name = normalizeRegion(query.regionName());
        var wrapper = Wrappers.<BaseRegionTb>lambdaQuery();
        if (TextUtils.hasText(query.regionCode()))
            wrapper.eq(BaseRegionTb::getRegionCode, query.regionCode());
        if (TextUtils.hasText(name))
            wrapper.and(q -> q.eq(BaseRegionTb::getRegionName, name).or().like(BaseRegionTb::getRegionName, name));
        List<EntityCandidate> rows = regionMapper.selectList(wrapper.orderByAsc(BaseRegionTb::getRegionName).last("limit " + limit(query.limit()))).stream()
                .map(row -> new EntityCandidate("REGION", row.getRegionCode(), row.getRegionName(), "地区", row.getRegionCode(), row.getRegionName(), null, null)).toList();
        return result("地区", rows);
    }

    @Override
    public ResolutionResult resolveIndustry(EntityQuery query) {
        String name = normalizeIndustry(query.industryName());
        var wrapper = Wrappers.<BaseIndustryTb>lambdaQuery();
        if (TextUtils.hasText(query.industryCode()))
            wrapper.eq(BaseIndustryTb::getIndustryCode, query.industryCode());
        if (TextUtils.hasText(name))
            wrapper.and(q -> q.eq(BaseIndustryTb::getIndustryName, name).or().like(BaseIndustryTb::getIndustryName, name));
        List<EntityCandidate> rows = industryMapper.selectList(wrapper.orderByAsc(BaseIndustryTb::getIndustryName).last("limit " + limit(query.limit()))).stream()
                .map(row -> new EntityCandidate("INDUSTRY", row.getIndustryCode(), row.getIndustryName(), "行业", null, null, row.getIndustryCode(), row.getIndustryName())).toList();
        return result("行业", rows);
    }

    @Override
    public BatchResolutionResult resolveBatch(BatchEntityQuery query) {
        if (query == null)
            throw new IllegalArgumentException("批量实体查询不能为空");
        List<BatchResolutionItem> items = new ArrayList<>();
        resolveNames(distinctNames(query.customerNames()), "CUSTOMER", query.limit(), items);
        resolveNames(distinctNames(query.regionNames()), "REGION", query.limit(), items);
        resolveNames(distinctNames(query.industryNames()), "INDUSTRY", query.limit(), items);
        if (items.isEmpty())
            throw new IllegalArgumentException("至少提供一个客户、地区或行业名称");

        boolean hasMissing = items.stream().anyMatch(item -> "NOT_FOUND".equals(item.status()));
        boolean hasMultiple = items.stream().anyMatch(item -> "MULTIPLE".equals(item.status()));
        String status = hasMissing ? "NOT_FOUND" : hasMultiple ? "MULTIPLE" : "UNIQUE";
        String message = switch (status) {
            case "UNIQUE" -> "批量实体解析成功";
            case "MULTIPLE" -> "部分实体存在多个候选，请逐项选择";
            default -> "部分实体没有找到，请修正名称后重试";
        };
        Map<String, Object> proposed = "UNIQUE".equals(status)
                ? proposedParameters(items)
                : Map.of();
        return new BatchResolutionResult(status, message, List.copyOf(items), proposed);
    }

    /**
     * 新协议按目标组分别消歧，确保“江苏城燃”和“浙江电力”不会在后续被展开成交叉组合。
     */
    @Override
    public IntentResolutionResult resolveIntent(IntentEntityQuery query) {
        if (query == null || query.targets() == null || query.targets().isEmpty())
            throw new IllegalArgumentException("至少提供一个实体目标组合");
        List<ResolvedTarget> targets = query.targets().stream().map(target -> resolveTarget(target, query.limit())).toList();
        boolean missing = targets.stream().anyMatch(target -> "NOT_FOUND".equals(target.status()));
        boolean multiple = targets.stream().anyMatch(target -> "MULTIPLE".equals(target.status()));
        String status = missing ? "NOT_FOUND" : multiple ? "MULTIPLE" : "UNIQUE";
        String message = switch (status) {
            case "UNIQUE" -> "目标组合实体解析成功";
            case "MULTIPLE" -> "部分实体存在多个候选，请逐项选择";
            default -> "部分实体没有找到，请修正名称后重试";
        };
        Map<String, Object> proposed = "UNIQUE".equals(status) ? intentParameters(query, targets) : Map.of();
        return new IntentResolutionResult(query.intent(), query.dimensionType(), status, message, targets, proposed);
    }

    private ResolvedTarget resolveTarget(EntityTargetQuery target, Integer limit) {
        if (target == null) throw new IllegalArgumentException("实体目标组合不能为空");
        List<BatchResolutionItem> items = new ArrayList<>();
        resolveNames(distinctNames(target.customerNames()), "CUSTOMER", limit, items);
        resolveNames(distinctNames(target.regionNames()), "REGION", limit, items);
        resolveNames(distinctNames(target.industryNames()), "INDUSTRY", limit, items);
        if (items.isEmpty()) throw new IllegalArgumentException("每个目标组合至少提供一个客户、地区或行业名称");
        boolean missing = items.stream().anyMatch(item -> "NOT_FOUND".equals(item.status()));
        boolean multiple = items.stream().anyMatch(item -> "MULTIPLE".equals(item.status()));
        String status = missing ? "NOT_FOUND" : multiple ? "MULTIPLE" : "UNIQUE";
        List<String> customers = codes(items, "CUSTOMER");
        List<String> regions = codes(items, "REGION");
        List<String> industries = codes(items, "INDUSTRY");
        String message = "UNIQUE".equals(status) ? "目标组合解析成功"
                : "MULTIPLE".equals(status) ? "目标组合存在多个候选" : "目标组合存在未命中实体";
        return new ResolvedTarget(status, message, customers, regions, industries, List.copyOf(items));
    }

    private List<String> codes(List<BatchResolutionItem> items, String type) {
        return items.stream().filter(item -> type.equals(item.entityType()))
                .filter(item -> "UNIQUE".equals(item.status()))
                .map(item -> item.candidates().get(0).code()).distinct().toList();
    }

    /** 返回意图层可直接消费的范围；执行类意图附带维度，查询类意图只保留组合筛选条件。 */
    private Map<String, Object> intentParameters(IntentEntityQuery query, List<ResolvedTarget> targets) {
        List<Map<String, Object>> scopes = targets.stream().map(target -> {
            Map<String, Object> scope = new LinkedHashMap<>();
            scope.put("customerCodes", target.customerCodes());
            scope.put("regionCodes", target.regionCodes());
            scope.put("industryCodes", target.industryCodes());
            return scope;
        }).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("targets", scopes);
        if ("TRAIN".equals(query.intent()) || "FORECAST".equals(query.intent()))
            result.put("dimensionType", query.dimensionType());
        return result;
    }

    private void resolveNames(List<String> names, String type, Integer limit,
                              List<BatchResolutionItem> target) {
        for (String name : names) {
            EntityQuery query = switch (type) {
                case "CUSTOMER" -> new EntityQuery(null, name, null, null, null, null, limit);
                case "REGION" -> new EntityQuery(null, null, null, name, null, null, limit);
                default -> new EntityQuery(null, null, null, null, null, name, limit);
            };
            ResolutionResult result = switch (type) {
                case "CUSTOMER" -> resolveCustomer(query);
                case "REGION" -> resolveRegion(query);
                default -> resolveIndustry(query);
            };
            target.add(new BatchResolutionItem(
                    type, name, result.status(), result.message(), result.candidates()));
        }
    }

    private List<String> distinctNames(List<String> values) {
        if (values == null)
            return List.of();
        return values.stream()
                .filter(TextUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private Map<String, Object> proposedParameters(List<BatchResolutionItem> items) {
        Map<String, Object> result = new LinkedHashMap<>();
        putResolved(result, items, "CUSTOMER", "customerCodes", "customerNames");
        putResolved(result, items, "REGION", "regionCodes", "regionNames");
        putResolved(result, items, "INDUSTRY", "industryCodes", "industryNames");
        return result;
    }

    private void putResolved(Map<String, Object> target, List<BatchResolutionItem> items,
                             String type, String codeKey, String nameKey) {
        List<EntityCandidate> candidates = items.stream()
                .filter(item -> type.equals(item.entityType()))
                .map(item -> item.candidates().get(0))
                .toList();
        if (candidates.isEmpty())
            return;
        target.put(codeKey, candidates.stream().map(EntityCandidate::code).distinct().toList());
        target.put(nameKey, candidates.stream().map(EntityCandidate::name).distinct().toList());
    }

    private EntityCandidate customer(BaseCustomerTb row) {
        return new EntityCandidate("CUSTOMER", row.getCustomerCode(), row.getCustomerName(), row.getRegionName() + " / " + row.getIndustryName(), row.getRegionCode(), row.getRegionName(),
                row.getIndustryCode(), row.getIndustryName());
    }

    private ResolutionResult result(String label, List<EntityCandidate> rows) {
        if (rows.isEmpty())
            return new ResolutionResult("NOT_FOUND", "没有找到匹配的" + label, rows);
        if (rows.size() > 1)
            return new ResolutionResult("MULTIPLE", "找到多个相似" + label + "，请让用户选择", rows);
        return new ResolutionResult("UNIQUE", label + "解析成功", rows);
    }

    private int limit(Integer value) {
        return value == null ? 20 : Math.max(1, Math.min(50, value));
    }

    private String normalizeRegion(String value) {
        return !TextUtils.hasText(value) ? value : value.trim().replaceAll("(特别行政区|壮族自治区|回族自治区|维吾尔自治区|自治区|省|市|地区)$", "");
    }

    private String normalizeIndustry(String value) {
        if (!TextUtils.hasText(value))
            return value;
        String normalized = value.trim().replaceAll("\\s+", "");
        return INDUSTRY_ALIASES.getOrDefault(normalized, normalized);
    }
}
