package com.gas.forecast.business.service;

import java.util.List;
import java.util.Map;

/** 将客户、地区和行业的自然语言名称解析成平台标准实体。 */
public interface BusinessEntityResolutionService {
    ResolutionResult resolveCustomer(EntityQuery query);

    ResolutionResult resolveRegion(EntityQuery query);

    ResolutionResult resolveIndustry(EntityQuery query);

    /** 批量解析用户输入的实体名称；每个名称独立返回唯一、歧义或未命中状态。 */
    BatchResolutionResult resolveBatch(BatchEntityQuery query);

    /**
     * 按用户表达的目标组合消歧。目标之间相互独立，防止地区、行业、客户被错误交叉组合。
     */
    IntentResolutionResult resolveIntent(IntentEntityQuery query);

    record EntityQuery(String customerCode, String customerName, String regionCode, String regionName, String industryCode, String industryName, Integer limit) {
    }

    record EntityCandidate(String type, String code, String name, String description, String regionCode, String regionName, String industryCode, String industryName) {
    }

    record ResolutionResult(String status, String message, List<EntityCandidate> candidates) {
    }

    record BatchEntityQuery(List<String> customerNames, List<String> regionNames,
                            List<String> industryNames, Integer limit) {
    }

    record BatchResolutionItem(String entityType, String inputName, String status,
                               String message, List<EntityCandidate> candidates) {
    }

    record BatchResolutionResult(String status, String message,
                                 List<BatchResolutionItem> items,
                                 Map<String, Object> proposedParameters) {
    }

    /** 意图已由智能体识别；这里只处理实体名称到标准编码的转换。 */
    record IntentEntityQuery(String intent, String dimensionType,
                             List<EntityTargetQuery> targets, Integer limit) {
    }

    /** 一个用户表达的地区、行业、客户组合，组内取交集、组间取并集。 */
    record EntityTargetQuery(List<String> customerNames, List<String> regionNames,
                             List<String> industryNames) {
    }

    /** 一个目标组合消歧后的标准编码及逐实体候选信息。 */
    record ResolvedTarget(String status, String message,
                          List<String> customerCodes, List<String> regionCodes, List<String> industryCodes,
                          List<BatchResolutionItem> items) {
    }

    /**
     * 供意图范围组装器消费的解析结果。UNIQUE 时 proposedParameters 可直接作为查询或执行工具参数。
     */
    record IntentResolutionResult(String intent, String dimensionType, String status, String message,
                                  List<ResolvedTarget> targets, Map<String, Object> proposedParameters) {
    }
}
