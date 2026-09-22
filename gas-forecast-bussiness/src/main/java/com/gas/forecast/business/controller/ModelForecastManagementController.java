package com.gas.forecast.business.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.gas.forecast.business.service.ModelForecastManagementService;
import com.gas.forecast.common.core.ResponseResult;
import org.springframework.web.bind.annotation.*;

@RestController
public class ModelForecastManagementController {
    private final ModelForecastManagementService service;

    public ModelForecastManagementController(ModelForecastManagementService service) {
        this.service = service;
    }

    @PostMapping("/model-forecast-config/listPage")
    public ResponseResult<?> listConfigs(@RequestBody JsonNode request) {
        return ResponseResult.success(service.listConfigs(request));
    }

    @PostMapping({"/model-forecast-config/create", "/model-forecast-config/update", "/model-forecast-config/save"})
    public ResponseResult<?> saveConfig(@RequestBody JsonNode request) {
        return ResponseResult.success(service.saveConfig(request));
    }

    @GetMapping("/model-forecast-config/delete")
    public ResponseResult<?> deleteConfig(@RequestParam Long id) {
        service.deleteConfig(id);
        return ResponseResult.success(null);
    }

    @PostMapping("/model-forecast-execution/execute")
    public ResponseResult<?> execute(@RequestBody JsonNode request) throws Exception {
        return ResponseResult.success(service.execute(request));
    }

    @PostMapping("/model-forecast-result/listPage")
    public ResponseResult<?> listResults(@RequestBody JsonNode request) {
        return ResponseResult.success(service.listResults(request));
    }

    @PostMapping("/model-forecast-result/history")
    public ResponseResult<?> resultHistory(@RequestBody JsonNode request) {
        return ResponseResult.success(service.resultHistory(request));
    }

    @PostMapping("/model-forecast-record/listPage")
    public ResponseResult<?> listRecords(@RequestBody JsonNode request) {
        return ResponseResult.success(service.listRecords(request));
    }
}
