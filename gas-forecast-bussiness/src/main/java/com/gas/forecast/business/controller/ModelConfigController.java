package com.gas.forecast.business.controller;

import com.gas.forecast.business.dto.request.ModelConfigCreateRequest;
import com.gas.forecast.business.dto.request.ModelConfigPageRequest;
import com.gas.forecast.business.dto.request.ModelConfigScopeUpdateRequest;
import com.gas.forecast.business.dto.request.ModelConfigUpdateRequest;
import com.gas.forecast.business.dto.response.ModelConfigResponse;
import com.gas.forecast.business.service.ModelConfigService;
import com.gas.forecast.common.core.PageInfoDTO;
import com.gas.forecast.common.core.ResponseResult;
import com.gas.forecast.common.security.annotation.RequirePermission;
import com.gas.forecast.common.web.WebLog;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模型配置管理接口。
 */
@RestController
@RequestMapping("/model-config")
@RequiredArgsConstructor
public class ModelConfigController {

    private final ModelConfigService modelConfigService;

    /**
     * 分页查询模型配置。
     *
     * @param reqDTO 模型配置分页查询条件
     * @return 模型配置分页数据
     */
    @PostMapping("listPage")
    @WebLog("模型列表查询")
    @RequirePermission("model:config:list")
    public ResponseResult<PageInfoDTO<ModelConfigResponse>> listPage(
            @Valid @RequestBody ModelConfigPageRequest reqDTO) {
        return ResponseResult.success(modelConfigService.listPage(reqDTO));
    }

    /**
     * 新增模型配置。
     *
     * @param reqDTO 模型配置新增参数
     * @return 新增后的模型配置
     */
    @PostMapping("create")
    @WebLog("新增模型")
    @RequirePermission("model:config:create")
    public ResponseResult<ModelConfigResponse> create(@Valid @RequestBody ModelConfigCreateRequest reqDTO) {
        return ResponseResult.success(modelConfigService.create(reqDTO));
    }

    /**
     * 更新模型配置。
     *
     * @param reqDTO 模型配置更新参数
     * @return 更新后的模型配置
     */
    @PostMapping("update")
    @WebLog("编辑模型")
    @RequirePermission("model:config:update")
    public ResponseResult<ModelConfigResponse> update(@Valid @RequestBody ModelConfigUpdateRequest reqDTO) {
        return ResponseResult.success(modelConfigService.update(reqDTO));
    }

    /**
     * 更新模型适用范围及关联特征。
     *
     * @param reqDTO 模型适用范围更新参数
     * @return 更新后的模型配置
     */
    @PostMapping("updateScope")
    @WebLog("配置模型适用范围")
    @RequirePermission("model:config:update")
    public ResponseResult<ModelConfigResponse> updateScope(@Valid @RequestBody ModelConfigScopeUpdateRequest reqDTO) {
        return ResponseResult.success(modelConfigService.updateScope(reqDTO));
    }

    /**
     * 删除模型配置。
     *
     * @param id 模型配置主键
     * @return 空响应
     */
    @GetMapping("delete")
    @WebLog("删除模型")
    @RequirePermission("model:config:delete")
    public ResponseResult<Void> delete(@RequestParam Long id) {
        modelConfigService.delete(id);
        return ResponseResult.success(null);
    }
}
