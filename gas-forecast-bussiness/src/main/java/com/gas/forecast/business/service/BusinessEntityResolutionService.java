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
}
