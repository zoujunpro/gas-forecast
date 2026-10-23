package com.gas.forecast.business.service;

import com.gas.forecast.business.common.client.modelplatform.dto.ModelInfoApiResponse;
import java.util.List;

/** 模型平台元数据查询接口，对应模型平台 Controller。 */
public interface ModelPlatformQueryService {
    List<ModelInfoApiResponse> models();
}
