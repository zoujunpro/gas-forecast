package com.gas.forecast.business.dto.request;

import java.util.List;

/**
 * 智能体实体解析后的一个目标组合。
 * <p>同一对象内的地区、行业、客户条件共同约束；不同对象之间独立执行。
 * CUSTOMER 维度下，地区和行业是客户筛选条件；REGION、INDUSTRY 维度的解释由维度规则决定。</p>
 */
public record ExecutionTarget(
        /** 已消歧的地区编码集合；同一目标内按或匹配。 */
        List<String> regionCodes,
        /** 已消歧的行业编码集合；同一目标内按或匹配。 */
        List<String> industryCodes,
        /** 已消歧的客户编码集合；CUSTOMER维度为空时由地区/行业范围展开全部匹配客户。 */
        List<String> customerCodes) {
}
