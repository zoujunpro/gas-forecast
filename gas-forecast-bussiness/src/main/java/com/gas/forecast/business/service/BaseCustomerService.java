package com.gas.forecast.business.service;

import com.gas.forecast.business.dto.request.BaseCustomerCreateRequest;
import com.gas.forecast.business.dto.request.BaseCustomerDeleteRequest;
import com.gas.forecast.business.dto.request.BaseCustomerPageRequest;
import com.gas.forecast.business.dto.request.BaseCustomerUpdateRequest;
import com.gas.forecast.business.dto.response.BaseCustomerResponse;
import com.gas.forecast.common.core.PageInfoDTO;

/**
 * 客户基础信息业务服务。
 */
public interface BaseCustomerService {

    /**
     * 分页查询客户列表。
     */
    PageInfoDTO<BaseCustomerResponse> listPage(BaseCustomerPageRequest reqDTO);

    /**
     * 新增客户。
     */
    BaseCustomerResponse createCustomer(BaseCustomerCreateRequest reqDTO);

    /**
     * 更新客户。
     */
    BaseCustomerResponse update(BaseCustomerUpdateRequest reqDTO);

    /**
     * 删除客户。
     */
    void delete(BaseCustomerDeleteRequest reqDTO);
}
