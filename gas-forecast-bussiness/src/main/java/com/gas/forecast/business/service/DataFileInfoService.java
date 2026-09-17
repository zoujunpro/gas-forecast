package com.gas.forecast.business.service;

import com.gas.forecast.business.dto.req.DataFileInfoCreateReqDTO;
import com.gas.forecast.business.dto.req.DataFileInfoDeleteReqDTO;
import com.gas.forecast.business.dto.req.DataFileInfoPageReqDTO;
import com.gas.forecast.business.dto.req.DataFileInfoUpdateReqDTO;
import com.gas.forecast.business.dto.resp.DataFileInfoRespDTO;
import com.gas.forecast.common.core.PageInfoDTO;

/**
 * 原始数据文件信息业务服务。
 */
public interface DataFileInfoService {

    /**
     * 分页查询原始数据文件列表。
     */
    PageInfoDTO<DataFileInfoRespDTO> listPage(DataFileInfoPageReqDTO reqDTO);

    /**
     * 新增原始数据文件信息。
     */
    DataFileInfoRespDTO create(DataFileInfoCreateReqDTO reqDTO);

    /**
     * 更新原始数据文件信息。
     */
    DataFileInfoRespDTO update(DataFileInfoUpdateReqDTO reqDTO);

    /**
     * 删除原始数据文件信息。
     */
    void delete(DataFileInfoDeleteReqDTO reqDTO);
}
