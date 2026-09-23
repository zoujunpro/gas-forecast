package com.gas.forecast.business.controller;

import com.gas.forecast.business.dto.request.ModelForecastConfigCreateRequest;
import com.gas.forecast.business.dto.request.ModelForecastConfigPageRequest;
import com.gas.forecast.business.dto.request.ModelForecastConfigUpdateRequest;
import com.gas.forecast.business.service.ModelForecastManagementService;
import com.gas.forecast.common.core.PageInfoDTO;
import com.gas.forecast.common.core.ResponseResult;
import com.gas.forecast.common.security.annotation.RequirePermission;
import com.gas.forecast.common.web.WebLog;
import com.gas.forecast.dao.domain.ModelForecastConfigTb;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模型预测配置管理接口。
 */
@RestController
@RequestMapping("/model-forecast-config")
@RequiredArgsConstructor
public class ModelForecastConfigController {

    private final ModelForecastManagementService modelForecastManagementService;

    /**
     * 分页查询模型预测配置。
     *
     * @param reqDTO 模型预测配置分页查询条件
     * @return 模型预测配置分页数据
     */
    @PostMapping("listPage")
    @WebLog("模型预测配置列表查询")
    @RequirePermission("model:forecast:list")
    public ResponseResult<PageInfoDTO<ModelForecastConfigTb>> listPage(
            @Valid @RequestBody ModelForecastConfigPageRequest reqDTO) {
        return ResponseResult.success(modelForecastManagementService.listConfigs(reqDTO));
    }

    /**
     * 新增模型预测配置。
     *
     * @param reqDTO 模型预测配置新增参数
     * @return 新增后的模型预测配置
     */
    @PostMapping("create")
    @WebLog("新增模型预测配置")
    public ResponseResult<ModelForecastConfigTb> create(
            @Valid @RequestBody ModelForecastConfigCreateRequest reqDTO) {
        return ResponseResult.success(modelForecastManagementService.createConfig(reqDTO));
    }

    /**
     * 更新模型预测配置。
     *
     * @param reqDTO 模型预测配置更新参数
     * @return 更新后的模型预测配置
     */
    @PostMapping("update")
    @WebLog("编辑模型预测配置")
    public ResponseResult<ModelForecastConfigTb> update(
            @Valid @RequestBody ModelForecastConfigUpdateRequest reqDTO) {
        return ResponseResult.success(modelForecastManagementService.updateConfig(reqDTO));
    }

    /**
     * 删除模型预测配置。
     *
     * @param id 模型预测配置主键
     * @return 空响应
     */
    @GetMapping("delete")
    @WebLog("删除模型预测配置")
    public ResponseResult<Void> delete(@RequestParam Long id) {
        modelForecastManagementService.deleteConfig(id);
        return ResponseResult.success(null);
    }
}
