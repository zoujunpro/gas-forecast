package com.gas.forecast.business.service.impl;

import com.gas.forecast.business.common.client.modelplatform.ModelPlatformClient;
import com.gas.forecast.business.common.client.modelplatform.dto.ModelInfoApiResponse;
import com.gas.forecast.business.service.ModelPlatformQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 模型平台元数据查询实现。 */
@Service
@RequiredArgsConstructor
public class ModelPlatformQueryServiceImpl implements ModelPlatformQueryService {
    private final ModelPlatformClient modelPlatformClient;

    @Override
    public List<ModelInfoApiResponse> models() {
        return modelPlatformClient.listModels();
    }
}
