package com.gas.forecast.business.service;

import com.gas.forecast.business.dto.req.BaseCustomerCreateReqDTO;
import com.gas.forecast.business.dto.req.BaseCustomerDeleteReqDTO;
import com.gas.forecast.business.dto.req.BaseCustomerPageReqDTO;
import com.gas.forecast.business.dto.req.BaseCustomerUpdateReqDTO;
import com.gas.forecast.business.dto.resp.BaseCustomerRespDTO;
import com.gas.forecast.common.core.PageInfoDTO;

/**
 * 客户基础信息业务服务。
 */
public interface BaseCustomerService {

    /**
     * 分页查询客户列表。
     */
    PageInfoDTO<BaseCustomerRespDTO> listPage(BaseCustomerPageReqDTO reqDTO);

    /**
     * 新增客户。
     */
    BaseCustomerRespDTO createCustomer(BaseCustomerCreateReqDTO reqDTO);

    /**
     * 更新客户。
     */
    BaseCustomerRespDTO update(BaseCustomerUpdateReqDTO reqDTO);

    /**
     * 删除客户。
     */
    void delete(BaseCustomerDeleteReqDTO reqDTO);
}
