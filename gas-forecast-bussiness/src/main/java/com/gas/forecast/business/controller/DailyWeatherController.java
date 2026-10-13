package com.gas.forecast.business.controller;

import com.gas.forecast.business.dto.request.DailyWeatherCreateRequest;
import com.gas.forecast.business.dto.request.DailyWeatherPageRequest;
import com.gas.forecast.business.dto.request.DailyWeatherUpdateRequest;
import com.gas.forecast.business.dto.response.DailyWeatherResponse;
import com.gas.forecast.business.service.DailyWeatherService;
import com.gas.forecast.common.core.PageInfoDTO;
import com.gas.forecast.common.core.ResponseResult;
import com.gas.forecast.common.security.annotation.RequirePermission;
import com.gas.forecast.common.web.WebLog;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/data-daily-weather")
@RequiredArgsConstructor
public class DailyWeatherController {
    private final DailyWeatherService service;

    @PostMapping("listPage")
    @WebLog("日级天气列表查询")
    @RequirePermission("data:daily-weather:list")
    public ResponseResult<PageInfoDTO<DailyWeatherResponse>> listPage(@Valid @RequestBody DailyWeatherPageRequest request) {
        return ResponseResult.success(service.listPage(request));
    }

    @PostMapping("create")
    @WebLog("新增日级天气")
    @RequirePermission("data:daily-weather:create")
    public ResponseResult<DailyWeatherResponse> create(@Valid @RequestBody DailyWeatherCreateRequest request) {
        return ResponseResult.success(service.create(request));
    }

    @PostMapping("update")
    @WebLog("编辑日级天气")
    @RequirePermission("data:daily-weather:update")
    public ResponseResult<DailyWeatherResponse> update(@Valid @RequestBody DailyWeatherUpdateRequest request) {
        return ResponseResult.success(service.update(request));
    }

    @GetMapping("delete")
    @WebLog("删除日级天气")
    @RequirePermission("data:daily-weather:delete")
    public ResponseResult<Void> delete(@RequestParam Long id) {
        service.delete(id);
        return ResponseResult.success(null);
    }
}
