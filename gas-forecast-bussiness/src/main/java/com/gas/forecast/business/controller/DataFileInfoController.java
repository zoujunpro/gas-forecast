package com.gas.forecast.business.controller;

import com.gas.forecast.business.dto.request.DataFileInfoCreateRequest;
import com.gas.forecast.business.dto.request.DataFileInfoPageRequest;
import com.gas.forecast.business.dto.request.DataFileInfoUpdateRequest;
import com.gas.forecast.business.dto.response.DataFileInfoResponse;
import com.gas.forecast.business.service.DataFileInfoService;
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
 * 原始数据文件信息接口。
 */
@RestController
@RequestMapping("/data-file-info")
@RequiredArgsConstructor
public class DataFileInfoController {

    private final DataFileInfoService dataFileInfoService;

    /**
     * 分页查询原始数据文件列表。
     *
     * @param reqDTO
     *            原始数据文件分页查询条件
     * @return 原始数据文件分页数据
     */
    @PostMapping("listPage")
    @WebLog("原始数据文件分页查询")
    @RequirePermission("data:file-info:list")
    public ResponseResult<PageInfoDTO<DataFileInfoResponse>> listPage(@Valid @RequestBody DataFileInfoPageRequest reqDTO) {
        var result = dataFileInfoService.listPage(reqDTO);
        return ResponseResult.success(result);
    }

    /**
     * 新增原始数据文件信息。
     *
     * @param reqDTO
     *            原始数据文件新增参数
     * @return 新增后的原始数据文件信息
     */
    @PostMapping("create")
    @WebLog("新增原始数据文件信息")
    @RequirePermission("data:file-info:create")
    public ResponseResult<DataFileInfoResponse> create(@Valid @RequestBody DataFileInfoCreateRequest reqDTO) {
        var result = dataFileInfoService.create(reqDTO);
        return ResponseResult.success(result);
    }

    /**
     * 更新原始数据文件信息。
     *
     * @param reqDTO
     *            原始数据文件更新参数
     * @return 更新后的原始数据文件信息
     */
    @PostMapping("update")
    @WebLog("编辑原始数据文件信息")
    @RequirePermission("data:file-info:update")
    public ResponseResult<DataFileInfoResponse> update(@Valid @RequestBody DataFileInfoUpdateRequest reqDTO) {
        DataFileInfoResponse result = dataFileInfoService.update(reqDTO);
        return ResponseResult.success(result);
    }

    /**
     * 删除原始数据文件信息。
     *
     * @param id
     *            原始数据文件主键
     * @return 空响应
     */
    @GetMapping("delete")
    @WebLog("删除原始数据文件信息")
    @RequirePermission("data:file-info:delete")
    public ResponseResult<Void> delete(@RequestParam Long id) {
        dataFileInfoService.delete(id);
        return ResponseResult.success(null);
    }
}
