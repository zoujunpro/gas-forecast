package com.gas.forecast.business.controller;

import com.gas.forecast.business.dto.response.ForecastDashboardResponse;
import com.gas.forecast.business.dto.response.ForecastDimensionResponse;
import com.gas.forecast.business.dto.response.ForecastSummaryResponse;
import com.gas.forecast.business.service.GasForecastService;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 天然气预测看板接口。
 */
@CrossOrigin
@RestController
@RequestMapping("/forecast")
@RequiredArgsConstructor
public class GasForecastController {

    private final GasForecastService gasForecastService;

    /**
     * 查询可用省份列表。
     *
     * @return 省份名称列表
     */
    @GetMapping("/provinces")
    public List<String> listProvinces() {
        return gasForecastService.listProvinces();
    }

    /**
     * 查询预测汇总列表。
     *
     * @return 预测汇总数据
     */
    @GetMapping("/summaries")
    public List<ForecastSummaryResponse> listSummaries() {
        return gasForecastService.listSummaries();
    }

    /**
     * 查询预测维度选项。
     *
     * @param areaCode 区域编码，可为空
     * @param provinceCode 省份编码，可为空
     * @return 预测维度数据
     */
    @GetMapping("/dimensions")
    public ForecastDimensionResponse listDimensions(
            @RequestParam(required = false) String areaCode, @RequestParam(required = false) String provinceCode) {
        return gasForecastService.listDimensions(areaCode, provinceCode);
    }

    /**
     * 查询预测看板数据。
     *
     * @param province 省份名称，可为空
     * @param provinceCode 省份编码，可为空
     * @param customerCode 客户编码，可为空
     * @return 预测看板数据
     */
    @GetMapping("/dashboard")
    public ForecastDashboardResponse getDashboard(
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String provinceCode,
            @RequestParam(required = false) String customerCode) {
        if (provinceCode != null && !provinceCode.isBlank()) {
            return gasForecastService.getDashboardByCode(provinceCode, customerCode);
        }
        return gasForecastService.getDashboard(province);
    }

    /**
     * 查询服务健康状态。
     *
     * @return 服务健康状态
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    /**
     * 处理数据不存在异常。
     *
     * @param exception 数据不存在异常
     * @return HTTP 404 错误信息
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NoSuchElementException exception) {
        return ResponseEntity.status(404).body(Map.of("message", exception.getMessage()));
    }
}
