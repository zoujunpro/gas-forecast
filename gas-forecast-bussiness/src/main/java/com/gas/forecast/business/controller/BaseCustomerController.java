package com.gas.forecast.business.controller;

import com.gas.forecast.business.dto.req.BaseCustomerCreateReqDTO;
import com.gas.forecast.business.dto.req.BaseCustomerDeleteReqDTO;
import com.gas.forecast.business.dto.req.BaseCustomerPageReqDTO;
import com.gas.forecast.business.dto.req.BaseCustomerUpdateReqDTO;
import com.gas.forecast.business.dto.resp.BaseCustomerRespDTO;
import com.gas.forecast.business.service.BaseCustomerService;
import com.gas.forecast.common.core.PageInfoDTO;
import com.gas.forecast.common.core.ResponseResult;
import com.gas.forecast.common.security.annotation.RequirePermission;
import com.gas.forecast.common.web.WebLog;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
    public ResponseResult<PageInfoDTO<BaseCustomerRespDTO>> listPage(@Valid @RequestBody BaseCustomerPageReqDTO reqDTO) {
        return ResponseResult.success(baseCustomerService.listPage(reqDTO));
    }

    /**
     * 新增客户。
     */
    @PostMapping("create")
    @WebLog("新增客户")
    @RequirePermission("base:customer:create")
    public ResponseResult<BaseCustomerRespDTO> create(@Valid @RequestBody BaseCustomerCreateReqDTO reqDTO) {
        return ResponseResult.success(baseCustomerService.createCustomer(reqDTO));
    }

    /**
     * 更新客户。
     */
    @PostMapping("update")
    @WebLog("编辑客户")
    @RequirePermission("base:customer:update")
    public ResponseResult<BaseCustomerRespDTO> update(@Valid @RequestBody BaseCustomerUpdateReqDTO reqDTO) {
        return ResponseResult.success(baseCustomerService.update(reqDTO));
    }

    /**
     * 删除客户。
     */
    @PostMapping("delete")
    @WebLog("删除客户")
    @RequirePermission("base:customer:delete")
    public ResponseResult<Void> delete(@Valid @RequestBody BaseCustomerDeleteReqDTO reqDTO) {
        baseCustomerService.delete(reqDTO);
        return ResponseResult.success(null);
    }
}
