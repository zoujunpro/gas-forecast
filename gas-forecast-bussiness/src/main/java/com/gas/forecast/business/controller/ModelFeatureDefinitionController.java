package com.gas.forecast.business.controller;

import com.gas.forecast.business.dto.req.ModelFeatureDefinitionCreateReqDTO;
import com.gas.forecast.business.dto.req.ModelFeatureDefinitionDeleteReqDTO;
import com.gas.forecast.business.dto.req.ModelFeatureDefinitionPageReqDTO;
import com.gas.forecast.business.dto.req.ModelFeatureDefinitionUpdateReqDTO;
import com.gas.forecast.business.dto.resp.ModelFeatureDefinitionRespDTO;
import com.gas.forecast.business.service.ModelFeatureDefinitionService;
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
 * 特征定义管理接口。
 */
@RestController
@RequestMapping("/model-feature-definition")
public class ModelFeatureDefinitionController {

    private final ModelFeatureDefinitionService modelFeatureDefinitionService;

    public ModelFeatureDefinitionController(ModelFeatureDefinitionService modelFeatureDefinitionService) {
        this.modelFeatureDefinitionService = modelFeatureDefinitionService;
    }

    @PostMapping("listPage")
    @WebLog("特征定义列表查询")
    @RequirePermission("model:feature-definition:list")
    public ResponseResult<PageInfoDTO<ModelFeatureDefinitionRespDTO>> listPage(@Valid @RequestBody ModelFeatureDefinitionPageReqDTO reqDTO) {
        return ResponseResult.success(modelFeatureDefinitionService.listPage(reqDTO));
    }

    @PostMapping("create")
    @WebLog("新增特征定义")
    @RequirePermission("model:feature-definition:create")
    public ResponseResult<ModelFeatureDefinitionRespDTO> create(@Valid @RequestBody ModelFeatureDefinitionCreateReqDTO reqDTO) {
        return ResponseResult.success(modelFeatureDefinitionService.create(reqDTO));
    }

    @PostMapping("update")
    @WebLog("编辑特征定义")
    @RequirePermission("model:feature-definition:update")
    public ResponseResult<ModelFeatureDefinitionRespDTO> update(@Valid @RequestBody ModelFeatureDefinitionUpdateReqDTO reqDTO) {
        return ResponseResult.success(modelFeatureDefinitionService.update(reqDTO));
    }

    @PostMapping("delete")
    @WebLog("删除特征定义")
    @RequirePermission("model:feature-definition:delete")
    public ResponseResult<Void> delete(@Valid @RequestBody ModelFeatureDefinitionDeleteReqDTO reqDTO) {
        modelFeatureDefinitionService.delete(reqDTO);
        return ResponseResult.success(null);
    }
}
