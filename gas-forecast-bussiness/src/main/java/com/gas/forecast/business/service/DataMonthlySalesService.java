package com.gas.forecast.business.service;

import com.gas.forecast.business.dto.request.DataSalesPageRequest;
import com.gas.forecast.business.dto.response.DataSalesResponse;
import com.gas.forecast.common.core.PageInfoDTO;

public interface DataMonthlySalesService {

    PageInfoDTO<DataSalesResponse> listPage(DataSalesPageRequest reqDTO);
}
