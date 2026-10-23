package com.gas.forecast.business.common.support;

import com.gas.forecast.business.enums.BaseCodeTypeEnum;

public interface BaseCodeGenerateService {
    String nextCode(BaseCodeTypeEnum codeType);
}
