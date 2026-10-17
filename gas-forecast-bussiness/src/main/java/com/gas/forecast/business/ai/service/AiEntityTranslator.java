package com.gas.forecast.business.ai.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.business.ai.model.AiIntent;
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
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiEntityTranslator {
    private static final Map<String, String> INDUSTRY_ALIASES = Map.ofEntries(Map.entry("城市燃气", "城燃"), Map.entry("城市燃气行业", "城燃"), Map.entry("城镇燃气", "城燃"), Map.entry("城镇燃气行业", "城燃"),
            Map.entry("发电行业", "发电"), Map.entry("化工行业", "化工"), Map.entry("工业行业", "工业"));

    private final BaseCustomerTbMapper customerMapper;
    private final BaseRegionTbMapper regionMapper;
    private final BaseIndustryTbMapper industryMapper;

    public TranslationResult translate(AiIntent intent) {
        intent.getCandidates().clear();
        if (TextUtils.hasText(intent.getCustomerCode())) {
            BaseCustomerTb customer = customerMapper.selectOne(Wrappers.<BaseCustomerTb>lambdaQuery().eq(BaseCustomerTb::getCustomerCode, intent.getCustomerCode()).last("limit 1"));
            if (customer != null) {
                applyCustomer(intent, customer);
                return TranslationResult.success();
            }
            // 编码可能由用户手误输入；同时存在客户名称时继续按名称寻找候选，交给用户确认。
            if (!TextUtils.hasText(intent.getCustomerName()))
                return TranslationResult.needInformation("没有找到客户编码“" + intent.getCustomerCode() + "”。请回复正确的客户编号或客户名称，我会继续处理刚才的请求。");
            intent.setCustomerCode(null);
        }
        if (TextUtils.hasText(intent.getCustomerName())) {
            List<BaseCustomerTb> matches = customerMapper.selectList(Wrappers.<BaseCustomerTb>lambdaQuery()
                    .and(q -> q.eq(BaseCustomerTb::getCustomerName, intent.getCustomerName()).or().like(BaseCustomerTb::getCustomerName, intent.getCustomerName()))
                    .orderByAsc(BaseCustomerTb::getCustomerName).last("limit 10"));
            if (matches.isEmpty())
                return TranslationResult.needInformation("暂时无法把“" + intent.getCustomerName() + "”匹配到客户档案。请回复正确的客户编号、完整客户名称，或补充所属地区，我会继续处理刚才的请求。");
            List<BaseCustomerTb> exact = matches.stream().filter(item -> intent.getCustomerName().equals(item.getCustomerName())).toList();
            if (exact.size() == 1) {
                applyCustomer(intent, exact.get(0));
            } else if (matches.size() == 1) {
                applyCustomer(intent, matches.get(0));
            } else {
                matches.forEach(item -> intent.getCandidates().add(candidate("CUSTOMER", item.getCustomerCode(), item.getCustomerName(), item.getRegionName() + " / " + item.getIndustryName())));
                return TranslationResult.confirm("找到了多个相似客户，请回复序号或完整客户名称进行确认。");
            }
        }
        if (!TextUtils.hasText(intent.getCustomerCode())) {
            TranslationResult regionResult = translateRegion(intent);
            if (!regionResult.resolved())
                return regionResult;
            TranslationResult industryResult = translateIndustry(intent);
            if (!industryResult.resolved())
                return industryResult;
        }
        return TranslationResult.success();
    }

    private TranslationResult translateRegion(AiIntent intent) {
        if (!TextUtils.hasText(intent.getRegionName()))
            return TranslationResult.success();
        String normalizedName = normalizeRegionName(intent.getRegionName());
        List<BaseRegionTb> rows = regionMapper.selectList(Wrappers.<BaseRegionTb>lambdaQuery().eq(BaseRegionTb::getRegionName, normalizedName).last("limit 10"));
        if (rows.isEmpty()) {
            rows = regionMapper.selectList(Wrappers.<BaseRegionTb>lambdaQuery().like(BaseRegionTb::getRegionName, normalizedName).orderByAsc(BaseRegionTb::getRegionName).last("limit 10"));
        }
        if (rows.isEmpty())
            return TranslationResult.needInformation("暂时没有找到地区“" + intent.getRegionName() + "”。请提供完整地区名称，我会继续处理刚才的请求。");
        if (rows.size() > 1)
            return candidates(intent, "REGION", rows, "找到多个可能的地区，请选择。");
        intent.setRegionCode(rows.get(0).getRegionCode());
        intent.setRegionName(rows.get(0).getRegionName());
        return TranslationResult.success();
    }

    private TranslationResult translateIndustry(AiIntent intent) {
        if (!TextUtils.hasText(intent.getIndustryName()))
            return TranslationResult.success();
        String normalizedName = normalizeIndustryName(intent.getIndustryName());
        List<BaseIndustryTb> rows = industryMapper.selectList(Wrappers.<BaseIndustryTb>lambdaQuery().eq(BaseIndustryTb::getIndustryName, normalizedName).last("limit 10"));
        if (rows.isEmpty()) {
            rows = industryMapper.selectList(Wrappers.<BaseIndustryTb>lambdaQuery().like(BaseIndustryTb::getIndustryName, normalizedName).orderByAsc(BaseIndustryTb::getIndustryName).last("limit 10"));
        }
        if (rows.isEmpty())
            return TranslationResult.needInformation("暂时没有找到行业“" + intent.getIndustryName() + "”。请提供完整行业名称，我会继续处理刚才的请求。");
        if (rows.size() > 1) {
            rows.forEach(item -> intent.getCandidates().add(candidate("INDUSTRY", item.getIndustryCode(), item.getIndustryName(), "行业")));
            return TranslationResult.confirm("找到多个可能的行业，请选择。");
        }
        intent.setIndustryCode(rows.get(0).getIndustryCode());
        intent.setIndustryName(rows.get(0).getIndustryName());
        return TranslationResult.success();
    }

    static String normalizeRegionName(String name) {
        if (!TextUtils.hasText(name))
            return name;
        String value = name.trim().replaceAll("\\s+", "");
        return value.replaceFirst("(特别行政区|壮族自治区|回族自治区|维吾尔自治区|自治区|省|市|地区)$", "");
    }

    static String normalizeIndustryName(String name) {
        if (!TextUtils.hasText(name))
            return name;
        String value = name.trim().replaceAll("\\s+", "");
        return INDUSTRY_ALIASES.getOrDefault(value, value);
    }

    private TranslationResult candidates(AiIntent intent, String type, List<BaseRegionTb> rows, String message) {
        rows.forEach(item -> intent.getCandidates().add(candidate(type, item.getRegionCode(), item.getRegionName(), "地区")));
        return TranslationResult.confirm(message);
    }

    public boolean applyCandidate(AiIntent intent, String answer) {
        if (intent.getCandidates().isEmpty())
            return false;
        AiIntent.EntityCandidate selected = null;
        try {
            int index = Integer.parseInt(answer.trim()) - 1;
            if (index >= 0 && index < intent.getCandidates().size())
                selected = intent.getCandidates().get(index);
        } catch (NumberFormatException ignored) {
            selected = intent.getCandidates().stream().filter(item -> answer.contains(item.getName()) || answer.contains(item.getCode())).findFirst().orElse(null);
        }
        if (selected == null)
            return false;
        if ("CUSTOMER".equals(selected.getType())) {
            intent.setCustomerCode(selected.getCode());
            intent.setCustomerName(selected.getName());
        } else if ("REGION".equals(selected.getType())) {
            intent.setRegionCode(selected.getCode());
            intent.setRegionName(selected.getName());
        } else if ("INDUSTRY".equals(selected.getType())) {
            intent.setIndustryCode(selected.getCode());
            intent.setIndustryName(selected.getName());
        }
        intent.getCandidates().clear();
        return true;
    }

    private void applyCustomer(AiIntent intent, BaseCustomerTb customer) {
        intent.setCustomerCode(customer.getCustomerCode());
        intent.setCustomerName(customer.getCustomerName());
        intent.setRegionCode(customer.getRegionCode());
        intent.setRegionName(customer.getRegionName());
        intent.setIndustryCode(customer.getIndustryCode());
        intent.setIndustryName(customer.getIndustryName());
    }

    private AiIntent.EntityCandidate candidate(String type, String code, String name, String description) {
        AiIntent.EntityCandidate value = new AiIntent.EntityCandidate();
        value.setType(type);
        value.setCode(code);
        value.setName(name);
        value.setDescription(description);
        return value;
    }

    public record TranslationResult(boolean resolved, boolean confirmationRequired, String message) {
        static TranslationResult success() {
            return new TranslationResult(true, false, null);
        }
        static TranslationResult confirm(String message) {
            return new TranslationResult(false, true, message);
        }
        static TranslationResult needInformation(String message) {
            return new TranslationResult(false, false, message);
        }
    }
}
