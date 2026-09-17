package com.gas.forecast.business.service;

import com.gas.forecast.business.dto.req.DataSalesPageReqDTO;
import com.gas.forecast.business.dto.resp.DataSalesRespDTO;
import com.gas.forecast.common.core.PageInfoDTO;

public interface DataMonthlySalesService {

    PageInfoDTO<DataSalesRespDTO> listPage(DataSalesPageReqDTO reqDTO);
}
