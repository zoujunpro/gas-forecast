package com.gas.forecast.business.controller;

import com.gas.forecast.business.dto.req.DataSalesPageReqDTO;
import com.gas.forecast.business.dto.resp.DataSalesRespDTO;
import com.gas.forecast.business.service.DataDailySalesService;
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
 * 日销量标准数据接口。
 */
@RestController
@RequestMapping("/data-daily-sales")
public class DataDailySalesController {

    private final DataDailySalesService dataDailySalesService;

    public DataDailySalesController(DataDailySalesService dataDailySalesService) {
        this.dataDailySalesService = dataDailySalesService;
    }

    /**
     * 分页查询日销量标准数据。
     */
    @PostMapping("listPage")
    @WebLog("日销量标准数据分页查询")
    @RequirePermission("data:daily-sales:list")
    public ResponseResult<PageInfoDTO<DataSalesRespDTO>> listPage(@Valid @RequestBody DataSalesPageReqDTO reqDTO) {
        return ResponseResult.success(dataDailySalesService.listPage(reqDTO));
    }
}
