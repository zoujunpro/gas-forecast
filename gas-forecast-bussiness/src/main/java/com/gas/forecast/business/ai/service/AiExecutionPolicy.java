package com.gas.forecast.business.ai.service;

import com.gas.forecast.business.ai.model.AiIntent;
import com.gas.forecast.business.ai.model.AiIntentType;
import com.gas.forecast.business.ai.model.AiSpeechAct;
import org.springframework.stereotype.Component;

/**
 * AI 只能描述用户意图；是否允许调用有副作用的业务接口由本策略统一决定。
 */
@Component
public class AiExecutionPolicy {
    private final AiToolRegistry toolRegistry;

    public AiExecutionPolicy(AiToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    public Decision decide(AiIntent intent) {
        if (!toolRegistry.mutating(intent.getTool()))
            return Decision.ALLOW;
        AiSpeechAct speechAct = intent.getSpeechAct() == null ? AiSpeechAct.UNKNOWN : intent.getSpeechAct();
        if (speechAct == AiSpeechAct.QUESTION || speechAct == AiSpeechAct.FOLLOW_UP)
            return Decision.ANSWER_ONLY;
        if (speechAct == AiSpeechAct.CANCEL)
            return Decision.CANCEL;
        if (speechAct == AiSpeechAct.CONFIRM)
            return Decision.ALLOW;
        if (Boolean.TRUE.equals(intent.getBulkScope()))
            return Decision.REQUIRE_CONFIRMATION;
        if (speechAct != AiSpeechAct.COMMAND || Boolean.TRUE.equals(intent.getRequiresConfirmation()))
            return Decision.REQUIRE_CONFIRMATION;
        return Decision.ALLOW;
    }

    public boolean isMutation(AiIntentType type) {
        return type == AiIntentType.START_TRAINING || type == AiIntentType.START_FORECAST;
    }

    public enum Decision {
        ALLOW, ANSWER_ONLY, REQUIRE_CONFIRMATION, CANCEL
    }
}
