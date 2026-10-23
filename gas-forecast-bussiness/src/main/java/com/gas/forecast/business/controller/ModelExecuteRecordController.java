package com.gas.forecast.business.controller;

import com.gas.forecast.business.dto.request.ModelExecuteRecordPageRequest;
import com.gas.forecast.business.service.ModelExecuteRecordService;
import com.gas.forecast.common.core.PageInfoDTO;
import com.gas.forecast.common.core.ResponseResult;
import com.gas.forecast.common.web.WebLog;
import com.gas.forecast.dao.domain.ModelExecuteRecordTb;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 模型管理中的预测记录查询接口。 */
@RestController
@RequestMapping("/model-execute-record")
@RequiredArgsConstructor
public class ModelExecuteRecordController {
    private final ModelExecuteRecordService modelExecuteRecordService;

    @PostMapping("listPage") @WebLog("预测记录分页查询")
    public ResponseResult<PageInfoDTO<ModelExecuteRecordTb>> listPage(@Valid @RequestBody ModelExecuteRecordPageRequest request) {
        return ResponseResult.success(modelExecuteRecordService.listPage(request));
    }

    @GetMapping("/{forecastNo}") @WebLog("预测记录详情查询")
    public ResponseResult<ModelExecuteRecordTb> detail(@PathVariable String forecastNo) {
        return ResponseResult.success(modelExecuteRecordService.detail(forecastNo));
    }
}
