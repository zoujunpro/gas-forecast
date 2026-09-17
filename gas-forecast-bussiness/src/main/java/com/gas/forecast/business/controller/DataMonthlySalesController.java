package com.gas.forecast.business.controller;

import com.gas.forecast.business.dto.req.DataSalesPageReqDTO;
import com.gas.forecast.business.dto.resp.DataSalesRespDTO;
import com.gas.forecast.business.service.DataMonthlySalesService;
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
 * 月销量标准数据接口。
 */
@RestController
@RequestMapping("/data-monthly-sales")
public class DataMonthlySalesController {

    private final DataMonthlySalesService dataMonthlySalesService;

    public DataMonthlySalesController(DataMonthlySalesService dataMonthlySalesService) {
        this.dataMonthlySalesService = dataMonthlySalesService;
    }

    /**
     * 分页查询月销量标准数据。
     */
    @PostMapping("listPage")
    @WebLog("月销量标准数据分页查询")
    @RequirePermission("data:monthly-sales:list")
    public ResponseResult<PageInfoDTO<DataSalesRespDTO>> listPage(@Valid @RequestBody DataSalesPageReqDTO reqDTO) {
        return ResponseResult.success(dataMonthlySalesService.listPage(reqDTO));
    }
}
