package com.gas.forecast.business.controller;

import com.gas.forecast.business.dto.req.BaseIndustryCreateReqDTO;
import com.gas.forecast.business.dto.req.BaseIndustryDeleteReqDTO;
import com.gas.forecast.business.dto.req.BaseIndustryPageReqDTO;
import com.gas.forecast.business.dto.req.BaseIndustryUpdateReqDTO;
import com.gas.forecast.business.dto.resp.BaseIndustryRespDTO;
import com.gas.forecast.business.service.BaseIndustryService;
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
 * 行业基础信息接口。
 */
@RestController
@RequestMapping("/base-industry")
public class BaseIndustryController {

    private final BaseIndustryService baseIndustryService;

    public BaseIndustryController(BaseIndustryService baseIndustryService) {
        this.baseIndustryService = baseIndustryService;
    }

    /**
     * 分页查询行业列表。
     */
    @PostMapping("listPage")
    @WebLog("行业列表查询")
    @RequirePermission("base:industry:list")
    public ResponseResult<PageInfoDTO<BaseIndustryRespDTO>> listPage(@Valid @RequestBody BaseIndustryPageReqDTO reqDTO) {
        return ResponseResult.success(baseIndustryService.listPage(reqDTO));
    }

    /**
     * 新增行业。
     */
    @PostMapping("create")
    @WebLog("新增行业")
    @RequirePermission("base:industry:create")
    public ResponseResult<BaseIndustryRespDTO> create(@Valid @RequestBody BaseIndustryCreateReqDTO reqDTO) {
        return ResponseResult.success(baseIndustryService.createIndustry(reqDTO));
    }

    /**
     * 更新行业。
     */
    @PostMapping("update")
    @WebLog("编辑行业")
    @RequirePermission("base:industry:update")
    public ResponseResult<BaseIndustryRespDTO> update(@Valid @RequestBody BaseIndustryUpdateReqDTO reqDTO) {
        return ResponseResult.success(baseIndustryService.update(reqDTO));
    }

    /**
     * 删除行业。
     */
    @GetMapping("delete")
    @WebLog("删除行业")
    @RequirePermission("base:industry:delete")
    public ResponseResult<Void> delete(@RequestParam Long id) {
        baseIndustryService.delete(new BaseIndustryDeleteReqDTO(id));
        return ResponseResult.success(null);
    }
}
