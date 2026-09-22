package com.gas.forecast.business.service;

import com.gas.forecast.business.dto.request.DataFileInfoCreateRequest;
import com.gas.forecast.business.dto.request.DataFileInfoPageRequest;
import com.gas.forecast.business.dto.request.DataFileInfoUpdateRequest;
import com.gas.forecast.business.dto.response.DataFileInfoResponse;
import com.gas.forecast.common.core.PageInfoDTO;

/**
 * 原始数据文件信息业务服务。
 */
public interface DataFileInfoService {

    /**
     * 分页查询原始数据文件列表。
     */
    PageInfoDTO<DataFileInfoResponse> listPage(DataFileInfoPageRequest reqDTO);

    /**
     * 新增原始数据文件信息。
     */
    DataFileInfoResponse create(DataFileInfoCreateRequest reqDTO);

    /**
     * 更新原始数据文件信息。
     */
    DataFileInfoResponse update(DataFileInfoUpdateRequest reqDTO);

    /**
     * 删除原始数据文件信息。
     */
    void delete(Long id);
}
