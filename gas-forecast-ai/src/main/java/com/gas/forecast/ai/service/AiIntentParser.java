package com.gas.forecast.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gas.forecast.ai.model.AiIntent;
import com.gas.forecast.ai.model.AiIntentType;
import com.gas.forecast.ai.model.AiSpeechAct;
import com.gas.forecast.ai.model.AiToolName;
import com.gas.forecast.business.component.ForecastAnalysisLlmClient;
import com.gas.forecast.common.util.TextUtils;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiIntentParser {
    private static final String SYSTEM_PROMPT = """
            你是天然气预测平台的工具选择器。只返回一个JSON对象，不要Markdown和解释。
            tool只能从以下白名单选择一个：
            QUERY_CUSTOMERS：查询基础客户数据，可按地区、行业筛选；例如“江苏化工行业有哪些客户”。
            QUERY_MODEL_CAPABILITY：查询智能体或模型支持范围；例如“短期智能体支持哪些客户”“泰兴中燃能预测吗”。
            START_TRAINING：用户明确要求发起训练。
            QUERY_TRAINING：查询训练状态、进度或结果。
            START_FORECAST：用户明确要求实际发起预测。
            QUERY_FORECAST_RESULT：查询已有预测状态或预测结果。
            ANALYZE_CURRENT_RESULT：对当前会话已有结果进行原因、趋势、日期、风险等追问。
            NONE：不属于以上工具或信息不足。
            intent只能是 START_TRAINING、QUERY_TRAINING、START_FORECAST、QUERY_FORECAST、QUERY_RESULT、QUERY_DATA、QUERY_CAPABILITY、GENERAL_CHAT、UNKNOWN。
            speechAct只能是 QUESTION、COMMAND、CONFIRM、CANCEL、FOLLOW_UP、UNKNOWN。
            intent表示业务主题，speechAct表示用户是在询问还是要求执行，两者必须分开判断。
            当用户询问某个智能体支持哪些客户、地区、行业或能力范围时，intent使用QUERY_CAPABILITY。
            当用户只是查询基础数据中某地区、某行业“有哪些客户、客户有哪些、查找客户”时，intent使用QUERY_DATA；没有“支持、能否预测、智能体”等能力含义时，不得使用QUERY_CAPABILITY。
            示例：“江苏化工行业的客户有哪些”应返回QUERY_DATA，并提取regionName=江苏、industryName=化工。
            “某客户可以预测吗”“能否预测某客户”“是否支持预测某客户”是在询问能力，必须使用QUERY_CAPABILITY，不能使用START_FORECAST。
            只有“帮我预测”“开始预测”“预测未来N天”等明确要求实际执行的表达，才使用START_FORECAST。
            “是否需要训练”“训练过吗”“预测效果怎么样”属于QUESTION，不能触发训练或预测。
            对上一份结果的原因、趋势、日期、风险等追问使用GENERAL_CHAT和FOLLOW_UP。
            只有用户明确要求执行但语义仍有歧义时requiresConfirmation=true；普通询问为false。
            提取 agentCode、regionName、industryName、customerCode、customerName、trainBatchNo、forecastBatchNo、startDate、endDate、forecastHorizon、trainMode、trainingPeriods、trainingStartDate、trainingEndDate、timeGranularity、sortBy、sortDirection、limit。
            agentCode取 short-term、monthly-sales、winter-supply；日期为yyyy-MM-dd；timeGranularity取DAY、TENDAY、MONTH。
            训练数据范围必须与预测范围分开：“全部历史数据训练”取trainMode=ALL；“最近1740天训练”取trainMode=RECENT、trainingPeriods=1740；明确训练起止日期取trainMode=RANGE及trainingStartDate/trainingEndDate。预测未来多少天只放forecastHorizon。
            客户查询中，“数据量比较多、数据最多、按数据量排序”设置sortBy=DATA_COUNT、sortDirection=DESC；“数据量较少、最少”设置为ASC；“前N个/前N名”提取limit=N。
            省、市、区域都放regionName；企业、用户、客户名称放customerName。用户原文明确提供客户编号时放customerCode；不要编造任何编码。
            confidence为0到1。信息不足时仍识别意图，并在clarificationQuestion中给出一个简短追问。
            tool是主要决策字段，intent仅为兼容字段并与tool保持一致。
            JSON字段必须完整，不存在的值使用null；requiresConfirmation必须是布尔值。
            """;

    private final ForecastAnalysisLlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final AiToolRegistry toolRegistry;
    private final AiToolArgumentValidator argumentValidator;

    public AiIntent parse(String message) {
        try {
            String answer = llmClient.complete(SYSTEM_PROMPT, message, 0.1D);
            JsonNode json = objectMapper.readTree(cleanJson(answer));
            AiIntent intent = objectMapper.treeToValue(json, AiIntent.class);
            if (intent.getIntent() == null)
                intent.setIntent(AiIntentType.UNKNOWN);
            if (intent.getConfidence() == null)
                intent.setConfidence(0D);
            if (intent.getSpeechAct() == null)
                intent.setSpeechAct(AiSpeechAct.UNKNOWN);
            if (intent.getRequiresConfirmation() == null)
                intent.setRequiresConfirmation(false);
            if (!TextUtils.hasText(intent.getAgentCode()))
                intent.setAgentCode("short-term");
            normalizeTrainingRange(intent, message);
            normalizeBusinessAliases(intent, message);
            toolRegistry.normalize(intent);
            List<String> validationErrors = argumentValidator.validate(intent);
            if (!validationErrors.isEmpty() || intent.getConfidence() < 0.6D || intent.getTool() == null || intent.getTool() == AiToolName.NONE) {
                intent = reflectAndRepair(message, intent, validationErrors);
            }
            return intent;
        } catch (Exception exception) {
            return fallback(message);
        }
    }

    /** 确定性提取训练数据窗口，避免“最近1740天”被误识别成预测天数。 */
    static void normalizeTrainingRange(AiIntent intent, String message) {
        String text = message == null ? "" : message.replaceAll("\\s+", "");
        if (text.matches(".*(全部|所有|完整)(历史)?数据.*(训练|重训|预测).*|.*(训练|重训|预测).*(全部|所有|完整)(历史)?数据.*")) {
            intent.setTrainMode("ALL");
            intent.setTrainingPeriods(null);
            return;
        }
        java.util.regex.Matcher days = java.util.regex.Pattern.compile("(?:用|使用)?(?:最近|近)(\\d{1,5})(?:天|日)(?:的)?(?:数据)?(?:进行)?(?:训练|重训|预测)").matcher(text);
        if (days.find()) {
            intent.setTrainMode("RECENT");
            intent.setTrainingPeriods(Integer.parseInt(days.group(1)));
            return;
        }
        java.util.regex.Matcher years = java.util.regex.Pattern.compile("(?:用|使用)?(?:最近|近)(\\d{1,2})年(?:的)?(?:数据)?(?:进行)?(?:训练|重训|预测)").matcher(text);
        if (years.find()) {
            int value = Integer.parseInt(years.group(1));
            String granularity = intent.getTimeGranularity();
            intent.setTrainMode("RECENT");
            intent.setTrainingPeriods("MONTH".equalsIgnoreCase(granularity) || "monthly-sales".equals(intent.getAgentCode()) ? value * 12 : value * 365);
        }
    }

    private AiIntent reflectAndRepair(String message, AiIntent firstResult, List<String> validationErrors) {
        try {
            String prompt = "用户原话：" + message + "\n第一次工具选择结果：" + objectMapper.writeValueAsString(firstResult) + "\nJava参数校验问题：" + objectMapper.writeValueAsString(validationErrors)
                    + "\n请重新检查工具、说话行为和参数提取。只能从用户原话提取，不得猜测或编造缺失参数。" + "如果原话确实没有必填信息，保留null并在clarificationQuestion中明确询问。只返回完整JSON。";
            String answer = llmClient.complete(SYSTEM_PROMPT, prompt, 0.1D);
            AiIntent repaired = objectMapper.readValue(cleanJson(answer), AiIntent.class);
            toolRegistry.normalize(repaired);
            if (repaired.getSpeechAct() == null)
                repaired.setSpeechAct(AiSpeechAct.UNKNOWN);
            if (repaired.getConfidence() == null)
                repaired.setConfidence(0D);
            if (repaired.getRequiresConfirmation() == null)
                repaired.setRequiresConfirmation(false);
            if (!TextUtils.hasText(repaired.getAgentCode()))
                repaired.setAgentCode("short-term");
            normalizeTrainingRange(repaired, message);
            normalizeBusinessAliases(repaired, message);
            return repaired;
        } catch (Exception ignored) {
            return firstResult;
        }
    }

    public boolean startsNewQuestion(String message) {
        return isCustomerDataQuestion(message) || isCapabilityQuestion(message) || isExplicitBusinessCommand(message);
    }

    private boolean isExplicitBusinessCommand(String message) {
        String text = message == null ? "" : message.trim();
        return text.matches(".*(帮我|请|立即|现在|开始|重新|全部|所有).*(预测|训练|重训).*") || text.matches(".*(预测|训练|重训).*(全部|所有).*(客户|地区|行业).*");
    }

    private boolean isCustomerDataQuestion(String message) {
        String text = message == null ? "" : message.trim();
        return text.matches(".*(客户信息|客户名单|客户列表|有哪些客户|那些客户|客户有哪些|客户有那些|查找.*客户|查询.*客户|找.*客户).*") && !isCapabilityQuestion(text);
    }

    private String extractRegionName(String message) {
        if (message == null)
            return null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(江苏|浙江|安徽|上海|山东|河南|河北|北京|天津|广东|福建|江西|湖北|湖南|四川|重庆|陕西|山西|辽宁|吉林|黑龙江|内蒙古|广西|云南|贵州|甘肃|青海|宁夏|新疆|西藏|海南)").matcher(message);
        return matcher.find() ? matcher.group(1) : null;
    }

    public AiIntent continueIntent(AiIntent pending, String message) {
        try {
            String prompt = "已有待补全意图：" + objectMapper.writeValueAsString(pending) + "\n用户最新回复：" + message + "\n判断最新回复是在补充待确认信息、确认执行、取消，还是一个全新问题。" + "若是补充或确认，保留已有业务动作和实体；若是新问题，按新问题完整解析。";
            String answer = llmClient.complete(SYSTEM_PROMPT, prompt, 0.1D);
            AiIntent result = objectMapper.readValue(cleanJson(answer), AiIntent.class);
            normalizeTrainingRange(result, message);
            normalizeBusinessAliases(result, message);
            toolRegistry.normalize(result);
            return result;
        } catch (Exception exception) {
            return parse(message);
        }
    }

    private AiIntent fallback(String message) {
        AiIntent intent = new AiIntent();
        String text = message == null ? "" : message.trim();
        if (isCustomerDataQuestion(text)) {
            intent.setIntent(AiIntentType.QUERY_DATA);
            intent.setRegionName(extractRegionName(text));
        } else if (isCapabilityQuestion(text))
            intent.setIntent(AiIntentType.QUERY_CAPABILITY);
        else if (text.matches(".*(哪些|什么|多少).*(客户).*(支持|可用).*"))
            intent.setIntent(AiIntentType.QUERY_CAPABILITY);
        else if (text.matches(".*(训练|重训).*"))
            intent.setIntent(AiIntentType.START_TRAINING);
        else if (text.matches(".*(预测|预估).*"))
            intent.setIntent(AiIntentType.START_FORECAST);
        else if (text.matches(".*(进度|状态|到哪).*"))
            intent.setIntent(text.contains("训练") ? AiIntentType.QUERY_TRAINING : AiIntentType.QUERY_FORECAST);
        else
            intent.setIntent(AiIntentType.UNKNOWN);
        intent.setSpeechAct(inferFallbackSpeechAct(text, intent.getIntent()));
        intent.setRequiresConfirmation(intent.getSpeechAct() == AiSpeechAct.UNKNOWN);
        intent.setConfidence(intent.getIntent() == AiIntentType.UNKNOWN ? 0D : 0.45D);
        intent.setClarificationQuestion("我没有完全理解。请说明客户名称，以及要训练、预测还是查询任务状态。");
        toolRegistry.normalize(intent);
        normalizeTrainingRange(intent, message);
        normalizeBusinessAliases(intent, message);
        return intent;
    }

    /** 业务别名由 Java 确定性归一，防止模型把 CNG 误拼成 CNC。 */
    private void normalizeBusinessAliases(AiIntent intent, String message) {
        String text = message == null ? "" : message;
        String explicitCustomerCode = extractCustomerCode(text);
        if (TextUtils.hasText(explicitCustomerCode))
            intent.setCustomerCode(explicitCustomerCode);
        if (text.matches(".*(全部|所有|每个).*(客户|用户).*"))
            intent.setBulkScope(true);
        // 原文中的明确数值参数必须由 Java 确定性提取，不能依赖大模型是否稳定返回。
        // 支持“预测50天”“未来 50 日”“预测未来50天”等常见表达。
        java.util.regex.Matcher horizonMatcher = java.util.regex.Pattern.compile("(?:预测未来|未来|预测)\\s*(\\d{1,3})\\s*(?:天|日)").matcher(text);
        if (horizonMatcher.find()) {
            intent.setForecastHorizon(Integer.parseInt(horizonMatcher.group(1)));
        }
        if (text.matches(".*(数据量|数据条数|数据记录).*(较少|比较少|最少|少的).*")) {
            intent.setSortBy("DATA_COUNT");
            intent.setSortDirection("ASC");
        } else if (text.matches(".*((数据量|数据条数|数据记录).*(较多|比较多|最多|多的|排序)|按.*(数据量|数据条数|数据记录)).*")) {
            intent.setSortBy("DATA_COUNT");
            intent.setSortDirection("DESC");
        }
        java.util.regex.Matcher limitMatcher = java.util.regex.Pattern.compile("前\\s*(\\d{1,2})\\s*(?:个|名|条)?").matcher(text);
        if (limitMatcher.find()) {
            intent.setLimit(Math.min(50, Math.max(1, Integer.parseInt(limitMatcher.group(1)))));
        }
        if (text.contains("城燃")) {
            intent.setIndustryCode(null);
            intent.setIndustryName("城燃");
        } else if (text.matches("(?i).*\\bCNG\\b.*")) {
            intent.setIndustryCode(null);
            intent.setIndustryName("CNG");
        }
        if (text.contains("化工")) {
            intent.setIndustryCode(null);
            intent.setIndustryName("化工");
        }
        if (text.contains("电力") || text.contains("发电")) {
            intent.setIndustryCode(null);
            intent.setIndustryName("电力");
        }
    }

    /** 明确写在用户原话中的客户编码由 Java 提取，避免被模型误当成客户名称的一部分。 */
    static String extractCustomerCode(String message) {
        if (!TextUtils.hasText(message))
            return null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?i)(?<![A-Z0-9_])CUS[0-9]+(?![A-Z0-9_])").matcher(message);
        return matcher.find() ? matcher.group().toUpperCase() : null;
    }

    private AiSpeechAct inferFallbackSpeechAct(String text, AiIntentType intentType) {
        if (text.matches("^(确认|确认执行|执行|继续执行|是的|好的|可以)$"))
            return AiSpeechAct.CONFIRM;
        if (text.matches("^(取消|不用了|停止|不要执行)$"))
            return AiSpeechAct.CANCEL;
        if (text.matches(".*(吗|么|是否|能否|可不可以|为什么|怎么|哪些|什么|多少|状态|进度)[？?]?.*"))
            return AiSpeechAct.QUESTION;
        if (intentType == AiIntentType.START_TRAINING || intentType == AiIntentType.START_FORECAST)
            return AiSpeechAct.COMMAND;
        return AiSpeechAct.UNKNOWN;
    }

    private boolean isCapabilityQuestion(String message) {
        String text = message == null ? "" : message.trim();
        return text.matches(".*(支持|可以|能).*(哪些|那些|什么|多少).*(客户|地区|行业).*") || text.matches(".*(哪些|那些|什么|多少).*(客户|地区|行业).*(支持|可用).*") || text.matches(".*(可以预测吗|能预测吗|能否预测|能不能预测|是否可以预测|是否支持预测).*");
    }

    private String extractCapabilityCustomerName(String message) {
        if (message == null)
            return null;
        String text = message.trim().replaceAll("[？?。！!]$", "");
        java.util.regex.Matcher prefix = java.util.regex.Pattern.compile("^(.{2,40}?)(?:这个客户|客户)?(?:可以预测吗?|能预测吗?|能否预测|能不能预测|是否可以预测|是否支持预测)$").matcher(text);
        if (prefix.find())
            return prefix.group(1).trim();
        java.util.regex.Matcher suffix = java.util.regex.Pattern.compile("^(?:能否预测|能不能预测|是否可以预测|是否支持预测)(.{2,40}?)(?:这个客户|客户)?$").matcher(text);
        return suffix.find() ? suffix.group(1).trim() : null;
    }

    private String resolveAgentCode(String message) {
        if (message != null && message.matches(".*(月度|月消费).*"))
            return "monthly-sales";
        if (message != null && message.matches(".*(冬季|保供).*"))
            return "winter-supply";
        return "short-term";
    }

    private String cleanJson(String value) {
        String text = value == null ? "" : value.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        return start >= 0 && end > start ? text.substring(start, end + 1) : text;
    }
}
