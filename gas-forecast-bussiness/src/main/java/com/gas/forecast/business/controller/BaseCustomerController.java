package com.gas.forecast.business.controller;

import com.gas.forecast.business.dto.request.BaseCustomerCreateRequest;
import com.gas.forecast.business.dto.request.BaseCustomerDeleteRequest;
import com.gas.forecast.business.dto.request.BaseCustomerPageRequest;
import com.gas.forecast.business.dto.request.BaseCustomerUpdateRequest;
import com.gas.forecast.business.dto.response.BaseCustomerResponse;
import com.gas.forecast.business.service.BaseCustomerService;
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
 * 客户基础信息接口。
 */
@RestController
@RequestMapping("/base-customer")
public class BaseCustomerController {

    private final BaseCustomerService baseCustomerService;

    public BaseCustomerController(BaseCustomerService baseCustomerService) {
        this.baseCustomerService = baseCustomerService;
    }

    /**
     * 分页查询客户列表。
     */
    @PostMapping("listPage")
    @WebLog("客户列表分页查询")
    @RequirePermission("base:customer:list")
    public ResponseResult<PageInfoDTO<BaseCustomerResponse>> listPage(
            @Valid @RequestBody BaseCustomerPageRequest reqDTO) {
        return ResponseResult.success(baseCustomerService.listPage(reqDTO));
    }

    /**
     * 新增客户。
     */
    @PostMapping("create")
    @WebLog("新增客户")
    @RequirePermission("base:customer:create")
    public ResponseResult<BaseCustomerResponse> create(@Valid @RequestBody BaseCustomerCreateRequest reqDTO) {
        return ResponseResult.success(baseCustomerService.createCustomer(reqDTO));
    }

    /**
     * 更新客户。
     */
    @PostMapping("update")
    @WebLog("编辑客户")
    @RequirePermission("base:customer:update")
    public ResponseResult<BaseCustomerResponse> update(@Valid @RequestBody BaseCustomerUpdateRequest reqDTO) {
        return ResponseResult.success(baseCustomerService.update(reqDTO));
    }

    /**
     * 删除客户。
     */
    @GetMapping("delete")
    @WebLog("删除客户")
    @RequirePermission("base:customer:delete")
    public ResponseResult<Void> delete(@RequestParam Long id) {
        baseCustomerService.delete(new BaseCustomerDeleteRequest(id));
        return ResponseResult.success(null);
    }
}
