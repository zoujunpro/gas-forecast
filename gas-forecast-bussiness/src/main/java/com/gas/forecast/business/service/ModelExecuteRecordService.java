package com.gas.forecast.business.service;

import com.gas.forecast.business.dto.request.ModelExecuteRecordPageRequest;
import com.gas.forecast.common.core.PageInfoDTO;
import com.gas.forecast.dao.domain.ModelExecuteRecordTb;

/** 模型预测记录管理接口，对应模型管理页面的预测记录查询。 */
public interface ModelExecuteRecordService {

    PageInfoDTO<ModelExecuteRecordTb> listPage(ModelExecuteRecordPageRequest request);

    ModelExecuteRecordTb detail(String forecastNo);
}
