package com.gas.forecast.business.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gas.forecast.business.ai.model.AiIntent;
import com.gas.forecast.business.ai.model.AiIntentType;
import com.gas.forecast.business.ai.model.AiToolName;
import org.junit.jupiter.api.Test;

class AiIntentParserCombinedActionTest {
    @Test
    void recognizesRetrainThenForecastAsOneWorkflow() {
        AiIntent intent = new AiIntent();
        intent.setIntent(AiIntentType.START_TRAINING);
        intent.setTool(AiToolName.START_TRAINING);
        AiIntentParser.normalizeCombinedTrainAndForecast(intent, "帮我把江苏的城燃客户全部重新训练和预测");
        assertEquals(AiIntentType.START_FORECAST, intent.getIntent());
        assertEquals(AiToolName.START_FORECAST, intent.getTool());
        assertTrue(intent.getForceRetrain());
    }
}
