package com.gas.forecast.business.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 训练特征数据应答参数。
 */
public record ModelTrainFeatureDataRespDTO(
        Long id,
        String statDate,
        String timeGranularity,
        String regionCode,
        String regionName,
        String customerCode,
        String customerName,
        String industryCode,
        String industryName,
        BigDecimal gasSales,
        Double feature001,
        Double feature002,
        Double feature003,
        Double feature004,
        Double feature005,
        Double feature006,
        Double feature007,
        Double feature008,
        Double feature009,
        Double feature010,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        Date createTime,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        Date updateTime
) {
}
