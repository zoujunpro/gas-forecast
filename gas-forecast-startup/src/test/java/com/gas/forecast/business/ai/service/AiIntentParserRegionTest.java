package com.gas.forecast.business.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class AiIntentParserRegionTest {
    @Test
    void extractsProvinceFromBulkTrainingCommand() {
        assertEquals("江苏", AiIntentParser.extractRegionName("帮我把江苏的城燃客户全部重新训练和预测"));
    }

    @Test
    void acceptsProvinceSuffix() {
        assertEquals("江苏", AiIntentParser.extractRegionName("江苏省城燃客户全部重新训练"));
    }

    @Test
    void leavesRegionEmptyWhenNotProvided() {
        assertNull(AiIntentParser.extractRegionName("把城燃客户全部重新训练"));
    }
}
