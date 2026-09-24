package com.gas.forecast.business.controller;

import com.gas.forecast.business.dto.request.BaseRegionCreateRequest;
import com.gas.forecast.business.dto.request.BaseRegionPageRequest;
import com.gas.forecast.business.dto.request.BaseRegionUpdateRequest;
import com.gas.forecast.business.dto.response.BaseRegionResponse;
import com.gas.forecast.business.service.BaseRegionService;
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
 * 区域基础信息接口。
 */
@RestController
@RequestMapping("/base-region")
@RequiredArgsConstructor
public class BaseRegionController {

    private final BaseRegionService baseRegionService;

    /**
     * 分页查询区域列表。
     *
     * @param reqDTO
     *            区域分页查询条件
     * @return 区域分页数据
     */
    @PostMapping("listPage")
    @WebLog("区域列表查询")
    @RequirePermission("base:region:list")
    public ResponseResult<PageInfoDTO<BaseRegionResponse>> listPage(@Valid @RequestBody BaseRegionPageRequest reqDTO) {
        var result = baseRegionService.listPage(reqDTO);
        return ResponseResult.success(result);
    }

    /**
     * 新增区域。
     *
     * @param reqDTO
     *            区域新增参数
     * @return 新增后的区域信息
     */
    @PostMapping("create")
    @WebLog("新增区域")
    @RequirePermission("base:region:create")
    public ResponseResult<BaseRegionResponse> create(@Valid @RequestBody BaseRegionCreateRequest reqDTO) {
        var result = baseRegionService.createRegion(reqDTO);
        return ResponseResult.success(result);
    }

    /**
     * 更新区域。
     *
     * @param reqDTO
     *            区域更新参数
     * @return 更新后的区域信息
     */
    @PostMapping("update")
    @WebLog("编辑区域")
    @RequirePermission("base:region:update")
    public ResponseResult<BaseRegionResponse> update(@Valid @RequestBody BaseRegionUpdateRequest reqDTO) {
        var result = baseRegionService.update(reqDTO);
        return ResponseResult.success(result);
    }

    /**
     * 删除区域。
     *
     * @param id
     *            区域主键
     * @return 空响应
     */
    @GetMapping("delete")
    @WebLog("删除区域")
    @RequirePermission("base:region:delete")
    public ResponseResult<Void> delete(@RequestParam Long id) {
        baseRegionService.delete(id);
        return ResponseResult.success(null);
    }
}
