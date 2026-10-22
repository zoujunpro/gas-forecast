package com.gas.forecast.business.service;

/** 为智能体提供基础资料、训练记录和预测记录的只读业务查询。 */
public interface AgentBusinessQueryService {
    Object regions(String keyword, Integer pageNo, Integer pageSize, boolean fetchAll);

    Object industries(String keyword, Integer pageNo, Integer pageSize, boolean fetchAll);

    Object customers(String keyword, String regionCode, String industryCode, Integer pageNo, Integer pageSize, boolean fetchAll);

    Object latestTraining(String customerCode, String regionCode, String industryCode, String agentCode);

    Object latestForecast(String customerCode, String regionCode, String industryCode, String agentCode, boolean includePoints);

    /** 查询编号实际对应的训练批次、预测批次或智能体任务。 */
    Object resolveBusinessReference(String reference);
}
