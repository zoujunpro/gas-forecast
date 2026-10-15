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
