package com.gas.forecast.business.controller;

import com.gas.forecast.business.dto.req.DataFileInfoCreateReqDTO;
import com.gas.forecast.business.dto.req.DataFileInfoDeleteReqDTO;
import com.gas.forecast.business.dto.req.DataFileInfoPageReqDTO;
import com.gas.forecast.business.dto.req.DataFileInfoUpdateReqDTO;
import com.gas.forecast.business.dto.resp.DataFileInfoRespDTO;
import com.gas.forecast.business.service.DataFileInfoService;
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
 * 原始数据文件信息接口。
 */
@RestController
@RequestMapping("/data-file-info")
public class DataFileInfoController {

    private final DataFileInfoService dataFileInfoService;

    public DataFileInfoController(DataFileInfoService dataFileInfoService) {
        this.dataFileInfoService = dataFileInfoService;
    }

    /**
     * 分页查询原始数据文件列表。
     */
    @PostMapping("listPage")
    @WebLog("原始数据文件分页查询")
    @RequirePermission("data:file-info:list")
    public ResponseResult<PageInfoDTO<DataFileInfoRespDTO>> listPage(@Valid @RequestBody DataFileInfoPageReqDTO reqDTO) {
        return ResponseResult.success(dataFileInfoService.listPage(reqDTO));
    }

    /**
     * 新增原始数据文件信息。
     */
    @PostMapping("create")
    @WebLog("新增原始数据文件信息")
    @RequirePermission("data:file-info:create")
    public ResponseResult<DataFileInfoRespDTO> create(@Valid @RequestBody DataFileInfoCreateReqDTO reqDTO) {
        return ResponseResult.success(dataFileInfoService.create(reqDTO));
    }

    /**
     * 更新原始数据文件信息。
     */
    @PostMapping("update")
    @WebLog("编辑原始数据文件信息")
    @RequirePermission("data:file-info:update")
    public ResponseResult<DataFileInfoRespDTO> update(@Valid @RequestBody DataFileInfoUpdateReqDTO reqDTO) {
        return ResponseResult.success(dataFileInfoService.update(reqDTO));
    }

    /**
     * 删除原始数据文件信息。
     */
    @GetMapping("delete")
    @WebLog("删除原始数据文件信息")
    @RequirePermission("data:file-info:delete")
    public ResponseResult<Void> delete(@RequestParam Long id) {
        dataFileInfoService.delete(new DataFileInfoDeleteReqDTO(id));
        return ResponseResult.success(null);
    }
}
