package com.gas.forecast.business.controller;

import com.gas.forecast.business.dto.request.BaseCustomerCreateRequest;
import com.gas.forecast.business.dto.request.BaseCustomerPageRequest;
import com.gas.forecast.business.dto.request.BaseCustomerUpdateRequest;
import com.gas.forecast.business.dto.response.BaseCustomerResponse;
import com.gas.forecast.business.service.BaseCustomerService;
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
 * 客户基础信息接口。
 */
@RestController
@RequestMapping("/base-customer")
@RequiredArgsConstructor
public class BaseCustomerController {

    private final BaseCustomerService baseCustomerService;

    /**
     * 分页查询客户列表。
     *
     * @param reqDTO
     *            客户分页查询条件
     * @return 客户分页数据
     */
    @PostMapping("listPage")
    @WebLog("客户列表分页查询")
    @RequirePermission("base:customer:list")
    public ResponseResult<PageInfoDTO<BaseCustomerResponse>> listPage(@Valid @RequestBody BaseCustomerPageRequest reqDTO) {
        var result = baseCustomerService.listPage(reqDTO);
        return ResponseResult.success(result);
    }

    /**
     * 新增客户。
     *
     * @param reqDTO
     *            客户新增参数
     * @return 新增后的客户信息
     */
    @PostMapping("create")
    @WebLog("新增客户")
    @RequirePermission("base:customer:create")
    public ResponseResult<BaseCustomerResponse> create(@Valid @RequestBody BaseCustomerCreateRequest reqDTO) {
        var result = baseCustomerService.createCustomer(reqDTO);
        return ResponseResult.success(result);
    }

    /**
     * 更新客户。
     *
     * @param reqDTO
     *            客户更新参数
     * @return 更新后的客户信息
     */
    @PostMapping("update")
    @WebLog("编辑客户")
    @RequirePermission("base:customer:update")
    public ResponseResult<BaseCustomerResponse> update(@Valid @RequestBody BaseCustomerUpdateRequest reqDTO) {
        var result = baseCustomerService.update(reqDTO);
        return ResponseResult.success(result);
    }

    /**
     * 删除客户。
     *
     * @param id
     *            客户主键
     * @return 空响应
     */
    @GetMapping("delete")
    @WebLog("删除客户")
    @RequirePermission("base:customer:delete")
    public ResponseResult<Void> delete(@RequestParam Long id) {
        baseCustomerService.delete(id);
        return ResponseResult.success(null);
    }
}
