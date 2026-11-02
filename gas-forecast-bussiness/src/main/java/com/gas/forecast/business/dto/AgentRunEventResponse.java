package com.gas.forecast.business.dto;

import java.util.Date;

/** 返回给 Python 或前端的过程事件快照。 */
public record AgentRunEventResponse(Long id, String runNo, String eventCode, String message, String eventDataJson,
        String status, String forecastNo, String taskDetailNo, String errorCode,
        String errorMessage, Date createdAt, Date updatedAt) { }
