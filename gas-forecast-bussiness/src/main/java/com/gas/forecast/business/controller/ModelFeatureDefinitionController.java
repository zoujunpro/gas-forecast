package com.gas.forecast.business.controller;

import com.gas.forecast.business.dto.request.ModelFeatureDefinitionCreateRequest;
import com.gas.forecast.business.dto.request.ModelFeatureDefinitionPageRequest;
import com.gas.forecast.business.dto.request.ModelFeatureDefinitionUpdateRequest;
import com.gas.forecast.business.dto.response.ModelFeatureDefinitionResponse;
import com.gas.forecast.business.service.ModelFeatureDefinitionService;
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
 * 特征定义管理接口。
 */
@RestController
@RequestMapping("/model-feature-definition")
@RequiredArgsConstructor
public class ModelFeatureDefinitionController {

    private final ModelFeatureDefinitionService modelFeatureDefinitionService;

    /**
     * 分页查询特征定义。
     *
     * @param reqDTO 特征定义分页查询条件
     * @return 特征定义分页数据
     */
    @PostMapping("listPage")
    @WebLog("特征定义列表查询")
    @RequirePermission("model:feature-definition:list")
    public ResponseResult<PageInfoDTO<ModelFeatureDefinitionResponse>> listPage(
            @Valid @RequestBody ModelFeatureDefinitionPageRequest reqDTO) {
        return ResponseResult.success(modelFeatureDefinitionService.listPage(reqDTO));
    }

    /**
     * 新增特征定义。
     *
     * @param reqDTO 特征定义新增参数
     * @return 新增后的特征定义
     */
    @PostMapping("create")
    @WebLog("新增特征定义")
    @RequirePermission("model:feature-definition:create")
    public ResponseResult<ModelFeatureDefinitionResponse> create(
            @Valid @RequestBody ModelFeatureDefinitionCreateRequest reqDTO) {
        return ResponseResult.success(modelFeatureDefinitionService.create(reqDTO));
    }

    /**
     * 更新特征定义。
     *
     * @param reqDTO 特征定义更新参数
     * @return 更新后的特征定义
     */
    @PostMapping("update")
    @WebLog("编辑特征定义")
    @RequirePermission("model:feature-definition:update")
    public ResponseResult<ModelFeatureDefinitionResponse> update(
            @Valid @RequestBody ModelFeatureDefinitionUpdateRequest reqDTO) {
        return ResponseResult.success(modelFeatureDefinitionService.update(reqDTO));
    }

    /**
     * 删除特征定义。
     *
     * @param id 特征定义主键
     * @return 空响应
     */
    @GetMapping("delete")
    @WebLog("删除特征定义")
    @RequirePermission("model:feature-definition:delete")
    public ResponseResult<Void> delete(@RequestParam Long id) {
        modelFeatureDefinitionService.delete(id);
        return ResponseResult.success(null);
    }
}
