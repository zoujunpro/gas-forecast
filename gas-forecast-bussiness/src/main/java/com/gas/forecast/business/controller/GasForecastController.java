package com.gas.forecast.business.controller;

import com.gas.forecast.business.dto.resp.ForecastDashboardRespDTO;
import com.gas.forecast.business.dto.resp.ForecastDimensionRespDTO;
import com.gas.forecast.business.dto.resp.ForecastSummaryRespDTO;
import com.gas.forecast.business.service.GasForecastService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@CrossOrigin
@RestController
@RequestMapping("/forecast")
public class GasForecastController {

    private final GasForecastService gasForecastService;

    public GasForecastController(GasForecastService gasForecastService) {
        this.gasForecastService = gasForecastService;
    }

    @GetMapping("/provinces")
    public List<String> listProvinces() {
        return gasForecastService.listProvinces();
    }

    @GetMapping("/summaries")
    public List<ForecastSummaryRespDTO> listSummaries() {
        return gasForecastService.listSummaries();
    }

    @GetMapping("/dimensions")
    public ForecastDimensionRespDTO listDimensions(@RequestParam(required = false) String areaCode,
                                               @RequestParam(required = false) String provinceCode) {
        return gasForecastService.listDimensions(areaCode, provinceCode);
    }

    @GetMapping("/dashboard")
    public ForecastDashboardRespDTO getDashboard(@RequestParam(required = false) String province,
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
