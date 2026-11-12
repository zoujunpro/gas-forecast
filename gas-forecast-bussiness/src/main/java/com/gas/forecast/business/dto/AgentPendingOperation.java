package com.gas.forecast.business.dto;

import com.gas.forecast.business.enums.AiClarificationInputTypeEnum;
import com.gas.forecast.business.enums.AiClarificationReasonEnum;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Java在多轮澄清之间保存并回传给Python的精简未完成操作。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentPendingOperation {

    /** 触发当前业务操作的原始用户问题。 */
    private String originalMessage;

    /** 参数完整后准备执行的工具。 */
    private String action;

    /** 历次澄清后累计确认的业务参数。 */
    private Map<String, Object> arguments = Map.of();

    /** 当前仍未解决的问题。 */
    private List<ResolutionIssue> issues = List.of();

    public void setArguments(Map<String, Object> arguments) {
        this.arguments = immutableMap(arguments);
    }

    public void setIssues(List<ResolutionIssue> issues) {
        this.issues = issues == null ? List.of() : List.copyOf(issues);
    }

    /** 一个阻止当前操作继续执行的结构化问题。 */
    public record ResolutionIssue(AiClarificationReasonEnum reason, String field, String message, AiClarificationInputTypeEnum inputType, boolean required, List<AiClarificationOption> options, Map<String, Object> details) {
        public ResolutionIssue {
            options = options == null ? List.of() : List.copyOf(options);
            details = immutableMap(details);
        }
    }

    private static Map<String, Object> immutableMap(Map<String, Object> source) {
        return source == null || source.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
