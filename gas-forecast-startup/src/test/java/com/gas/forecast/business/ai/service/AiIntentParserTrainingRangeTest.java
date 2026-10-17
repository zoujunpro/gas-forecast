package com.gas.forecast.business.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.gas.forecast.business.ai.model.AiIntent;
import org.junit.jupiter.api.Test;

class AiIntentParserTrainingRangeTest {

    @Test
    void extractsRecentDays() {
        AiIntent intent = new AiIntent();
        AiIntentParser.normalizeTrainingRange(intent, "用最近1740天的数据训练这个客户");
        assertEquals("RECENT", intent.getTrainMode());
        assertEquals(1740, intent.getTrainingPeriods());
    }

    @Test
    void convertsRecentYearsForDailyAgent() {
        AiIntent intent = new AiIntent();
        intent.setAgentCode("short-term");
        AiIntentParser.normalizeTrainingRange(intent, "用最近3年数据预测未来30天");
        assertEquals("RECENT", intent.getTrainMode());
        assertEquals(1095, intent.getTrainingPeriods());
    }

    @Test
    void extractsAllHistory() {
        AiIntent intent = new AiIntent();
        AiIntentParser.normalizeTrainingRange(intent, "使用全部历史数据预测");
        assertEquals("ALL", intent.getTrainMode());
        assertNull(intent.getTrainingPeriods());
    }
}
