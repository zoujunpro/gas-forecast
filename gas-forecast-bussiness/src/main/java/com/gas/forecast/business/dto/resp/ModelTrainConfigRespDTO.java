package com.gas.forecast.business.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Date;

/**
 * 模型训练配置应答参数。
 */
public record ModelTrainConfigRespDTO(
        Long id,
        String trainCode,
        String trainName,
        String agentCode,
        String modelCode,
        String modelName,
        String regionCode,
        String regionName,
        String industryCode,
        String industryName,
        String customerCode,
        String customerName,
        String trainStartDate,
        String trainEndDate,
        String trainMode,
        String timeGranularity,
        Integer recentPeriods,
        Integer enabled,
        String remark,
        String createdBy,
        String createdByName,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        Date createdAt,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        Date updatedAt
) {
}
