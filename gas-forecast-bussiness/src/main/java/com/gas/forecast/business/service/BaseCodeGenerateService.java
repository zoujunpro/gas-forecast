package com.gas.forecast.business.service;

import com.gas.forecast.business.enums.BaseCodeType;

public interface BaseCodeGenerateService {
    String nextCode(BaseCodeType codeType);
}
