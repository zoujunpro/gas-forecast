package com.gas.forecast.business.application.mcp;

/** 为智能体提供基础资料、统一预测执行记录的只读业务查询。 */
public interface AgentBusinessQueryService {
    Object regions(String keyword, Integer pageNo, Integer pageSize, boolean fetchAll);

    Object industries(String keyword, Integer pageNo, Integer pageSize, boolean fetchAll);

    Object customers(String keyword, String regionCode, String industryCode, Integer pageNo, Integer pageSize, boolean fetchAll);

    Object latestForecast(String customerCode, String regionCode, String industryCode, String agentCode, boolean includePoints);

    /** 查询编号实际对应的预测执行记录或智能体任务。 */
    Object resolveBusinessReference(String reference);
}
