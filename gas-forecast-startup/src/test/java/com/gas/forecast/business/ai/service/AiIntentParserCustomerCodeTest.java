package com.gas.forecast.business.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class AiIntentParserCustomerCodeTest {
    @Test
    void extractsExplicitCustomerCodeFromForecastCommand() {
        assertEquals("CUS000297", AiIntentParser.extractCustomerCode("新奥天津（浙江）（城燃，编号：CUS000297）帮我预测未来50天的销量"));
    }

    @Test
    void normalizesLowercaseCustomerCode() {
        assertEquals("CUS000297", AiIntentParser.extractCustomerCode("客户编码 cus000297"));
    }

    @Test
    void doesNotInventCustomerCode() {
        assertNull(AiIntentParser.extractCustomerCode("新奥天津帮我预测未来50天"));
    }
}
