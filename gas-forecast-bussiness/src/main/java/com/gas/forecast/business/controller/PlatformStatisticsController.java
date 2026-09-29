package com.gas.forecast.business.controller;

import com.gas.forecast.business.dto.response.PlatformStatisticsResponse;
import com.gas.forecast.business.service.PlatformStatisticsService;
import com.gas.forecast.common.core.ResponseResult;
import com.gas.forecast.common.web.WebLog;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class PlatformStatisticsController {
    private final PlatformStatisticsService platformStatisticsService;

    @GetMapping("/statistics")
    @WebLog("平台数据概览查询")
    public ResponseResult<PlatformStatisticsResponse> statistics() {
        return ResponseResult.success(platformStatisticsService.getStatistics());
    }
}
