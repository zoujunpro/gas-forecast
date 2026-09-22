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

@CrossOrigin
@RestController
@RequestMapping("/forecast")
@RequiredArgsConstructor
public class GasForecastController {

    private final GasForecastService gasForecastService;

    @GetMapping("/provinces")
    public List<String> listProvinces() {
        return gasForecastService.listProvinces();
    }

    @GetMapping("/summaries")
    public List<ForecastSummaryResponse> listSummaries() {
        return gasForecastService.listSummaries();
    }

    @GetMapping("/dimensions")
    public ForecastDimensionResponse listDimensions(
            @RequestParam(required = false) String areaCode, @RequestParam(required = false) String provinceCode) {
        return gasForecastService.listDimensions(areaCode, provinceCode);
    }

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

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NoSuchElementException exception) {
        return ResponseEntity.status(404).body(Map.of("message", exception.getMessage()));
    }
}
