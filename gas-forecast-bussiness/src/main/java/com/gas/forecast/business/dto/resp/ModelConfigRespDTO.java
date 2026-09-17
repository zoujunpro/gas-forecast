package com.gas.forecast.business.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Date;
import java.util.List;

public record ModelConfigRespDTO(
        Long id,
        String configCode,
        String configName,
        String agentCode,
        String agentName,
        String sceneCode,
        String strategyType,
        String regionCode,
        String regionName,
        String industryCode,
        String industryName,
        String customerCode,
        String customerName,
        List<String> regionCodes,
        List<String> regionNames,
        List<String> industryCodes,
        List<String> industryNames,
        List<String> customerCodes,
        List<String> customerNames,
        String description,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        Date createTime,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        Date updateTime
) {
}
