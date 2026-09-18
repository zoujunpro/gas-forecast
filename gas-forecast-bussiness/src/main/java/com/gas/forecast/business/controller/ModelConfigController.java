package com.gas.forecast.business.controller;

import com.gas.forecast.business.dto.req.ModelConfigCreateReqDTO;
import com.gas.forecast.business.dto.req.ModelConfigDeleteReqDTO;
import com.gas.forecast.business.dto.req.ModelConfigPageReqDTO;
import com.gas.forecast.business.dto.req.ModelConfigScopeUpdateReqDTO;
import com.gas.forecast.business.dto.req.ModelConfigUpdateReqDTO;
import com.gas.forecast.business.dto.resp.ModelConfigRespDTO;
import com.gas.forecast.business.service.ModelConfigService;
import com.gas.forecast.common.core.PageInfoDTO;
import com.gas.forecast.common.core.ResponseResult;
import com.gas.forecast.common.security.annotation.RequirePermission;
import com.gas.forecast.common.web.WebLog;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模型配置管理接口。
 */
@RestController
@RequestMapping("/model-config")
public class ModelConfigController {

    private final ModelConfigService modelConfigService;

    public ModelConfigController(ModelConfigService modelConfigService) {
        this.modelConfigService = modelConfigService;
    }

    @PostMapping("listPage")
    @WebLog("模型列表查询")
    @RequirePermission("model:config:list")
    public ResponseResult<PageInfoDTO<ModelConfigRespDTO>> listPage(@Valid @RequestBody ModelConfigPageReqDTO reqDTO) {
        return ResponseResult.success(modelConfigService.listPage(reqDTO));
    }

    @PostMapping("create")
    @WebLog("新增模型")
    @RequirePermission("model:config:create")
    public ResponseResult<ModelConfigRespDTO> create(@Valid @RequestBody ModelConfigCreateReqDTO reqDTO) {
        return ResponseResult.success(modelConfigService.create(reqDTO));
    }

    @PostMapping("update")
    @WebLog("编辑模型")
    @RequirePermission("model:config:update")
    public ResponseResult<ModelConfigRespDTO> update(@Valid @RequestBody ModelConfigUpdateReqDTO reqDTO) {
        return ResponseResult.success(modelConfigService.update(reqDTO));
    }

    @PostMapping("updateScope")
    @WebLog("配置模型适用范围")
    @RequirePermission("model:config:update")
    public ResponseResult<ModelConfigRespDTO> updateScope(@Valid @RequestBody ModelConfigScopeUpdateReqDTO reqDTO) {
        return ResponseResult.success(modelConfigService.updateScope(reqDTO));
    }

    @PostMapping("delete")
    @WebLog("删除模型")
    @RequirePermission("model:config:delete")
    public ResponseResult<Void> delete(@Valid @RequestBody ModelConfigDeleteReqDTO reqDTO) {
        modelConfigService.delete(reqDTO);
        return ResponseResult.success(null);
    }
}
