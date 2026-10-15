package com.gas.forecast.business.service;

import java.util.List;

/** 将客户、地区和行业的自然语言名称解析成平台标准实体。 */
public interface BusinessEntityResolutionService {
    ResolutionResult resolveCustomer(EntityQuery query);

    ResolutionResult resolveRegion(EntityQuery query);

    ResolutionResult resolveIndustry(EntityQuery query);

    record EntityQuery(String customerCode, String customerName, String regionCode, String regionName, String industryCode, String industryName, Integer limit) {
    }

    record EntityCandidate(String type, String code, String name, String description, String regionCode, String regionName, String industryCode, String industryName) {
    }

    record ResolutionResult(String status, String message, List<EntityCandidate> candidates) {
    }
}
