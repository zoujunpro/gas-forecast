package com.gas.forecast.business.controller;

import com.gas.forecast.business.dto.req.ModelTrainConfigCreateReqDTO;
import com.gas.forecast.business.dto.req.ModelTrainConfigDeleteReqDTO;
import com.gas.forecast.business.dto.req.ModelTrainConfigPageReqDTO;
import com.gas.forecast.business.dto.req.ModelTrainConfigUpdateReqDTO;
import com.gas.forecast.business.dto.resp.ModelTrainConfigRespDTO;
import com.gas.forecast.business.service.ModelTrainConfigService;
import com.gas.forecast.common.core.PageInfoDTO;
import com.gas.forecast.common.core.ResponseResult;
import com.gas.forecast.common.security.annotation.RequirePermission;
import com.gas.forecast.common.web.WebLog;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模型训练配置管理接口。
 */
@RestController
@RequestMapping("/model-train-config")
public class ModelTrainConfigController {

    private final ModelTrainConfigService modelTrainConfigService;

    public ModelTrainConfigController(ModelTrainConfigService modelTrainConfigService) {
        this.modelTrainConfigService = modelTrainConfigService;
    }

    @PostMapping("listPage")
    @WebLog("模型训练配置列表查询")
    @RequirePermission("model:train-config:list")
    public ResponseResult<PageInfoDTO<ModelTrainConfigRespDTO>> listPage(@Valid @RequestBody ModelTrainConfigPageReqDTO reqDTO) {
        return ResponseResult.success(modelTrainConfigService.listPage(reqDTO));
    }

    @PostMapping("create")
    @WebLog("新增模型训练配置")
    @RequirePermission("model:train-config:create")
    public ResponseResult<ModelTrainConfigRespDTO> create(@Valid @RequestBody ModelTrainConfigCreateReqDTO reqDTO) {
        return ResponseResult.success(modelTrainConfigService.create(reqDTO));
    }

    @PostMapping("update")
    @WebLog("编辑模型训练配置")
    @RequirePermission("model:train-config:update")
    public ResponseResult<ModelTrainConfigRespDTO> update(@Valid @RequestBody ModelTrainConfigUpdateReqDTO reqDTO) {
        return ResponseResult.success(modelTrainConfigService.update(reqDTO));
    }

    @GetMapping("delete")
    @WebLog("删除模型训练配置")
    @RequirePermission("model:train-config:delete")
    public ResponseResult<Void> delete(@RequestParam Long id) {
        modelTrainConfigService.delete(new ModelTrainConfigDeleteReqDTO(id));
        return ResponseResult.success(null);
    }
}
