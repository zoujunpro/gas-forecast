package com.gas.forecast.business.controller;

import com.gas.forecast.business.dto.request.AutoTrainRequest;
import com.gas.forecast.business.dto.response.AutoTrainResponse;
import com.gas.forecast.business.service.AutoTrainService;
import com.gas.forecast.common.core.ResponseResult;
import com.gas.forecast.common.security.annotation.RequirePermission;
import com.gas.forecast.common.web.WebLog;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 供人工、AI、定时巡检和数据事件共用的训练总入口。 */
@RestController
@RequestMapping("/auto-train")
@RequiredArgsConstructor
public class AutoTrainController {
    private final AutoTrainService autoTrainService;

    @PostMapping("execute")
    @WebLog("统一发起模型训练")
    @RequirePermission("config:train:execute")
    public ResponseResult<AutoTrainResponse> execute(@Valid @RequestBody AutoTrainRequest request) {
        return ResponseResult.success(autoTrainService.execute(request));
    }
}
