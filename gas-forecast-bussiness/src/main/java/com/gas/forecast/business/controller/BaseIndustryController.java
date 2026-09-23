package com.gas.forecast.business.controller;

import com.gas.forecast.business.dto.request.BaseIndustryCreateRequest;
import com.gas.forecast.business.dto.request.BaseIndustryPageRequest;
import com.gas.forecast.business.dto.request.BaseIndustryUpdateRequest;
import com.gas.forecast.business.dto.response.BaseIndustryResponse;
import com.gas.forecast.business.service.BaseIndustryService;
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
 * 行业基础信息接口。
 */
@RestController
@RequestMapping("/base-industry")
@RequiredArgsConstructor
public class BaseIndustryController {

    private final BaseIndustryService baseIndustryService;

    /**
     * 分页查询行业列表。
     *
     * @param reqDTO 行业分页查询条件
     * @return 行业分页数据
     */
    @PostMapping("listPage")
    @WebLog("行业列表查询")
    @RequirePermission("base:industry:list")
    public ResponseResult<PageInfoDTO<BaseIndustryResponse>> listPage(
            @Valid @RequestBody BaseIndustryPageRequest reqDTO) {
        return ResponseResult.success(baseIndustryService.listPage(reqDTO));
    }

    /**
     * 新增行业。
     *
     * @param reqDTO 行业新增参数
     * @return 新增后的行业信息
     */
    @PostMapping("create")
    @WebLog("新增行业")
    @RequirePermission("base:industry:create")
    public ResponseResult<BaseIndustryResponse> create(@Valid @RequestBody BaseIndustryCreateRequest reqDTO) {
        return ResponseResult.success(baseIndustryService.createIndustry(reqDTO));
    }

    /**
     * 更新行业。
     *
     * @param reqDTO 行业更新参数
     * @return 更新后的行业信息
     */
    @PostMapping("update")
    @WebLog("编辑行业")
    @RequirePermission("base:industry:update")
    public ResponseResult<BaseIndustryResponse> update(@Valid @RequestBody BaseIndustryUpdateRequest reqDTO) {
        return ResponseResult.success(baseIndustryService.update(reqDTO));
    }

    /**
     * 删除行业。
     *
     * @param id 行业主键
     * @return 空响应
     */
    @GetMapping("delete")
    @WebLog("删除行业")
    @RequirePermission("base:industry:delete")
    public ResponseResult<Void> delete(@RequestParam Long id) {
        baseIndustryService.delete(id);
        return ResponseResult.success(null);
    }
}
