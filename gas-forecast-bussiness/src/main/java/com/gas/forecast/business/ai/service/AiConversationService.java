package com.gas.forecast.business.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.gas.forecast.business.ai.dto.AiChatRequest;
import com.gas.forecast.business.ai.dto.AiChatResponse;
import com.gas.forecast.business.ai.dto.AiChatResponse.ExecutionProgress;
import com.gas.forecast.business.ai.dto.AiChatResponse.ForecastPoint;
import com.gas.forecast.business.ai.dto.AiChatResponse.ForecastResult;
import com.gas.forecast.business.ai.dto.AiChatResponse.BacktestPoint;
import com.gas.forecast.business.ai.dto.AiChatResponse.Step;
import com.gas.forecast.business.ai.dto.AiChatResponse.TrainingResult;
import com.gas.forecast.business.ai.dto.AiConversationResponse;
import com.gas.forecast.business.ai.dto.AiTrainingProgressResponse;
import com.gas.forecast.business.ai.model.AiIntent;
import com.gas.forecast.business.ai.model.AiIntentType;
import com.gas.forecast.business.ai.model.AiSpeechAct;
import com.gas.forecast.business.ai.model.AiToolName;
import com.gas.forecast.business.component.ForecastAnalysisLlmClient;
import com.gas.forecast.business.component.AgentConversationClient;
import com.gas.forecast.business.dto.request.AutoTrainRequest;
import com.gas.forecast.business.dto.request.ModelForecastConfigSaveRequest;
import com.gas.forecast.business.dto.request.ModelForecastExecuteRequest;
import com.gas.forecast.business.dto.request.ModelTrainResultRequest;
import com.gas.forecast.business.service.AutoTrainService;
import com.gas.forecast.business.service.ForecastAnalysisService;
import com.gas.forecast.business.service.ModelForecastManagementService;
import com.gas.forecast.business.service.ModelTrainExecutionService;
import com.gas.forecast.business.service.TaskForecastContinuationService;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.security.context.SecurityContextHolder;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.AiConversationMessageTb;
import com.gas.forecast.dao.domain.AiConversationTb;
import com.gas.forecast.dao.domain.BaseCustomerTb;
import com.gas.forecast.dao.domain.ModelConfigScopeTb;
import com.gas.forecast.dao.domain.ModelConfigTb;
import com.gas.forecast.dao.domain.ModelForecastConfigTb;
import com.gas.forecast.dao.domain.ModelForecastRecordTb;
import com.gas.forecast.dao.domain.ModelForecastResultTb;
import com.gas.forecast.dao.domain.ModelTrainConfigTb;
import com.gas.forecast.dao.domain.ModelTrainBacktestTb;
import com.gas.forecast.dao.domain.DataDailySalesTb;
import com.gas.forecast.dao.domain.ModelTrainRecordTb;
import com.gas.forecast.dao.mapper.AiConversationMessageTbMapper;
import com.gas.forecast.dao.mapper.AiConversationTbMapper;
import com.gas.forecast.dao.mapper.AgentExecutionTaskTbMapper;
import com.gas.forecast.dao.mapper.BaseCustomerTbMapper;
import com.gas.forecast.dao.mapper.ModelConfigScopeTbMapper;
import com.gas.forecast.dao.mapper.ModelConfigTbMapper;
import com.gas.forecast.dao.mapper.ModelForecastConfigTbMapper;
import com.gas.forecast.dao.mapper.ModelForecastRecordTbMapper;
import com.gas.forecast.dao.mapper.ModelForecastResultTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainConfigTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainBacktestTbMapper;
import com.gas.forecast.dao.mapper.DataDailySalesTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainRecordTbMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiConversationService {
    private final AiConversationTbMapper conversationMapper;
    private final AiConversationMessageTbMapper messageMapper;
    private final AgentExecutionTaskTbMapper executionTaskMapper;
    private final AiIntentParser intentParser;
    private final AiEntityTranslator entityTranslator;
    private final AiReplyComposer replyComposer;
    private final AiExecutionPolicy executionPolicy;
    private final AiToolRegistry toolRegistry;
    private final AiToolArgumentValidator toolArgumentValidator;
    private final AutoTrainService autoTrainService;
    private final ModelTrainExecutionService trainExecutionService;
    private final TaskForecastContinuationService taskForecastContinuationService;
    private final ModelForecastManagementService forecastService;
    private final ModelTrainConfigTbMapper trainConfigMapper;
    private final ModelConfigTbMapper modelConfigMapper;
    private final ModelConfigScopeTbMapper modelScopeMapper;
    private final BaseCustomerTbMapper customerMapper;
    private final ModelTrainRecordTbMapper trainRecordMapper;
    private final ModelTrainBacktestTbMapper trainBacktestMapper;
    private final DataDailySalesTbMapper dailySalesMapper;
    private final ModelForecastConfigTbMapper forecastConfigMapper;
    private final ModelForecastRecordTbMapper forecastRecordMapper;
    private final ModelForecastResultTbMapper forecastResultMapper;
    private final ForecastAnalysisLlmClient llmClient;
    private final AgentConversationClient agentConversationClient;
    private final ForecastAnalysisService forecastAnalysisService;
    private final ObjectMapper objectMapper;

    /**
     * 供受信任的业务适配层（例如 MCP）调用已经解析好的工具请求。这里仍复用现有
     * 训练、预测编排，避免协议层复制业务逻辑。批量变更必须由调用方显式确认。
     */
    public AiChatResponse invokeTool(String conversationNo, AiIntent intent, boolean confirmed) {
        AiConversationTb conversation = findOwned(conversationNo);
        toolRegistry.normalize(intent);
        if (intent.getTool() == null || intent.getTool() == AiToolName.NONE)
            throw new BusinessException("没有指定可执行的业务工具");
        if (Boolean.TRUE.equals(intent.getBulkScope()) && toolRegistry.mutating(intent.getTool()) && !confirmed)
            throw new BusinessException("批量训练或预测必须先获得用户确认");
        if (toolRegistry.mutating(intent.getTool())) {
            intent.setSpeechAct(AiSpeechAct.COMMAND);
            intent.setRequiresConfirmation(false);
        } else {
            intent.setSpeechAct(AiSpeechAct.QUESTION);
        }
        intent.setConfidence(1D);
        if (intent.getIntent() != AiIntentType.GENERAL_CHAT) {
            AiEntityTranslator.TranslationResult translation = entityTranslator.translate(intent);
            if (!translation.resolved())
                return respond(conversation, "NEED_INFORMATION", replyComposer.compose("NEED_INFORMATION", withCandidates(translation.message(), intent), intent), intent);
        }
        List<String> argumentErrors = toolArgumentValidator.validate(intent);
        if (!argumentErrors.isEmpty())
            throw new BusinessException(String.join("；", argumentErrors));
        String missing = requiredParameterQuestion(intent, conversation);
        if (missing != null)
            return respond(conversation, "NEED_INFORMATION", missing, intent);
        ExecutionResult execution = execute(conversation, intent);
        conversation.setUpdatedAt(new Date());
        conversationMapper.updateById(conversation);
        return respond(conversation, execution.status(), execution.answer(), intent, execution.bulkExecution());
    }

    /** 查询会话工作记忆，并补充最近训练、预测批次和待确认动作。 */
    public Map<String, Object> getMemory(String conversationNo) {
        AiConversationTb conversation = findOwned(conversationNo);
        Map<String, Object> memory = readMemory(conversation.getMemoryJson());
        memory.put("conversationNo", conversation.getConversationNo());
        memory.put("agentCode", conversation.getAgentCode());
        memory.put("status", conversation.getStatus());
        memory.put("lastTrainBatchNo", conversation.getLastTrainBatchNo());
        memory.put("lastForecastBatchNo", conversation.getLastForecastBatchNo());
        if (TextUtils.hasText(conversation.getPendingIntentJson()))
            memory.put("pendingAction", readJsonValue(conversation.getPendingIntentJson()));
        return memory;
    }

    /** 更新当前用户拥有的会话记忆；支持UPSERT、DELETE和CLEAR。 */
    public Map<String, Object> updateMemory(String conversationNo, String operation, Map<String, Object> values) {
        AiConversationTb conversation = findOwned(conversationNo);
        Map<String, Object> memory = readMemory(conversation.getMemoryJson());
        String normalized = TextUtils.hasText(operation) ? operation.trim().toUpperCase() : "UPSERT";
        if ("CLEAR".equals(normalized)) {
            memory.clear();
        } else if ("DELETE".equals(normalized)) {
            if (values != null)
                values.keySet().forEach(memory::remove);
        } else if ("UPSERT".equals(normalized)) {
            if (values != null)
                memory.putAll(values);
        } else {
            throw new BusinessException("operation只支持UPSERT、DELETE或CLEAR");
        }
        try {
            conversation.setMemoryJson(objectMapper.writeValueAsString(memory));
        } catch (Exception exception) {
            throw new BusinessException("记忆内容无法序列化");
        }
        conversation.setUpdatedAt(new Date());
        conversationMapper.updateById(conversation);
        return getMemory(conversationNo);
    }

    @SuppressWarnings("unchecked")
    /** 兼容空值或历史损坏数据，将memory_json安全解析为Map。 */
    private Map<String, Object> readMemory(String json) {
        if (!TextUtils.hasText(json))
            return new HashMap<>();
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception exception) {
            log.warn("会话记忆JSON损坏，按空记忆处理");
            return new HashMap<>();
        }
    }

    private Object readJsonValue(String json) {
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception exception) {
            return null;
        }
    }

    public AiChatResponse chat(AiChatRequest request) {
        AiConversationTb conversation = requireConversation(request);
        saveMessage(conversation.getId(), "user", request.getMessage(), null, null);
        // 统一 AI 会话由 Python Agent 负责理解、指代/时间解析、编排和回复；Java 仅提供持久化与业务工具。
        if ("GENERAL".equals(conversation.getScene()))
            return chatThroughAgent(conversation, request);
        // forecastBatchNo只提供当前结果上下文；人工输入仍统一经过模型意图识别。
        // contextualFollowUp不能把会话锁死为结果分析，否则“重新预测50天”等动作永远无法路由。
        AiIntent intent = resolveIntent(conversation, request.getMessage());
        intent = applyRequestScope(request, intent);
        intent = applyConversationQueryContext(conversation, request.getMessage(), intent);

        // 预测详情页的助手只负责解释当前批次。预测和训练必须通过页面上的业务按钮发起，
        // 避免一句追问绕过页面参数、确认流程并创建新的任务。
        if (intent.getIntent() == AiIntentType.START_FORECAST || intent.getIntent() == AiIntentType.START_TRAINING) {
            conversation.setPendingIntentJson(null);
            conversation.setStatus("ACTIVE");
            conversation.setUpdatedAt(new Date());
            conversationMapper.updateById(conversation);
            String answer = intent.getIntent() == AiIntentType.START_TRAINING
                    ? "当前助手仅支持分析本次预测结果，不能发起模型训练。请在左侧设置参数后，通过页面上的训练或预测按钮操作。"
                    : "当前助手仅支持分析本次预测结果，不能发起新的预测。请在左侧设置预测参数后，点击“预测”按钮。";
            return respond(conversation, "COMPLETED", answer, intent);
        }

        // 没命中业务工具不等于对话失败。开放式问题交给大模型正常回答，只有明确的业务动作才进入工具校验。
        if (intent.getIntent() == null || intent.getIntent() == AiIntentType.UNKNOWN || intent.getTool() == null || intent.getTool() == AiToolName.NONE || intent.getConfidence() == null
                || intent.getConfidence() < 0.6D) {
            intent = generalChatIntent(intent);
        }

        AiExecutionPolicy.Decision initialDecision = executionPolicy.decide(intent);
        if (initialDecision == AiExecutionPolicy.Decision.ANSWER_ONLY) {
            return answerWithoutExecution(conversation, intent);
        }
        if (initialDecision == AiExecutionPolicy.Decision.CANCEL) {
            conversation.setPendingIntentJson(null);
            conversation.setStatus("ACTIVE");
            conversation.setUpdatedAt(new Date());
            conversationMapper.updateById(conversation);
            return respond(conversation, "COMPLETED", "已取消，本次没有执行训练或预测任务。", intent);
        }

        if (intent.getIntent() != AiIntentType.GENERAL_CHAT) {
            AiEntityTranslator.TranslationResult translation = entityTranslator.translate(intent);
            if (!translation.resolved()) {
                if (translation.confirmationRequired())
                    return confirm(conversation, intent, replyComposer.compose("NEED_SELECTION", withCandidates(translation.message(), intent), intent));
                return needInformation(conversation, intent, replyComposer.compose("NEED_INFORMATION", translation.message(), intent));
            }
        }

        List<String> argumentErrors = toolArgumentValidator.validate(intent);
        if (!argumentErrors.isEmpty()) {
            intent.setClarificationQuestion("还需要补充以下信息：" + String.join("；", argumentErrors));
            return needInformation(conversation, intent, replyComposer.compose("NEED_INFORMATION", intent.getClarificationQuestion(), intent));
        }

        String missing = requiredParameterQuestion(intent, conversation);
        if (missing != null)
            return needInformation(conversation, intent, replyComposer.compose("NEED_INFORMATION", missing, intent));

        AiExecutionPolicy.Decision decision = executionPolicy.decide(intent);
        if (decision == AiExecutionPolicy.Decision.CANCEL) {
            conversation.setPendingIntentJson(null);
            conversation.setStatus("ACTIVE");
            return respond(conversation, "COMPLETED", "已取消，本次没有执行训练或预测任务。", intent);
        }
        if (decision == AiExecutionPolicy.Decision.REQUIRE_CONFIRMATION) {
            intent.setRequiresConfirmation(true);
            return confirm(conversation, intent, executionConfirmationMessage(intent));
        }

        conversation.setPendingIntentJson(null);
        conversation.setStatus("ACTIVE");
        ExecutionResult execution;
        try {
            execution = execute(conversation, intent);
        } catch (BusinessException exception) {
            String answer = explainToolFailure(intent, exception.getMessage());
            conversation.setUpdatedAt(new Date());
            conversationMapper.updateById(conversation);
            return respond(conversation, "FAILED", answer, intent);
        } catch (RuntimeException exception) {
            log.error("AI会话工具执行异常, conversationNo={}, tool={}", conversation.getConversationNo(), intent.getTool(), exception);
            conversation.setUpdatedAt(new Date());
            conversationMapper.updateById(conversation);
            String factualMessage = "本次处理没有完成：系统暂时无法执行该请求。你可以换一种说法重试；如果是在查询训练或预测，请直接提供批次号。";
            return respond(conversation, "FAILED", replyComposer.compose("FAILED", factualMessage, intent), intent);
        }
        conversation.setUpdatedAt(new Date());
        conversationMapper.updateById(conversation);
        return respond(conversation, execution.status(), execution.answer(), intent, execution.bulkExecution());
    }

    private AiChatResponse chatThroughAgent(AiConversationTb conversation, AiChatRequest request) {
        String message = request.getMessage();
        List<Map<String, String>> history = recentHistory(conversation.getId(), 12);
        try {
            AgentConversationClient.AgentReply reply = agentConversationClient.chat(
                    conversation.getConversationNo(), message, history, request.getAgentType(), request.getMetadata());
            // Python在本次调用中会通过MCP更新memoryJson。必须重新读取，避免用调用前的旧实体整行更新并覆盖刚保存的pendingAction。
            AiConversationTb latestConversation = findOwned(conversation.getConversationNo());
            AiIntent intent = agentIntent(reply.capabilityCode(), reply.parameters());
            String trainBatchNo = findText(reply.executionResult(), "trainBatchNo");
            String forecastBatchNo = findText(reply.executionResult(), "forecastBatchNo");
            ForecastResult forecastResult = readAgentForecastResult(reply.forecastResult());
            ExecutionProgress progress = readAgentProgress(reply.progress());
            if (!TextUtils.hasText(trainBatchNo) && forecastResult != null && forecastResult.training() != null)
                trainBatchNo = forecastResult.training().batchNo();
            if (!TextUtils.hasText(forecastBatchNo) && forecastResult != null) forecastBatchNo = forecastResult.batchNo();
            if (TextUtils.hasText(trainBatchNo)) latestConversation.setLastTrainBatchNo(trainBatchNo);
            if (TextUtils.hasText(forecastBatchNo)) latestConversation.setLastForecastBatchNo(forecastBatchNo);
            latestConversation.setStatus(reply.status());
            latestConversation.setUpdatedAt(new Date());
            conversationMapper.updateById(latestConversation);
            saveMessage(latestConversation.getId(), "assistant", reply.answer(), reply.capabilityCode(),
                    new StoredMessagePayload(progress, forecastResult, null, List.of(), null, reply.presentation()));
            return new AiChatResponse(latestConversation.getConversationNo(), reply.status(), reply.answer(), intent, List.of(), trainBatchNo, forecastBatchNo,
                    progress, forecastResult, null, List.of(), reply.presentation());
        } catch (RuntimeException exception) {
            log.error("Python Agent统一会话失败, conversationNo={}", conversation.getConversationNo(), exception);
            String answer = "智能助手暂时无法完成这次处理，请稍后重试。已有业务数据和任务不会受到影响。";
            AiIntent intent = generalChatIntent(null);
            saveMessage(conversation.getId(), "assistant", answer, AiIntentType.GENERAL_CHAT.name(), null);
            return new AiChatResponse(conversation.getConversationNo(), "FAILED", answer, intent, List.of(), null, null, null, null, null, List.of(), null);
        }
    }

    private ForecastResult readAgentForecastResult(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        try {
            return objectMapper.treeToValue(node, ForecastResult.class);
        } catch (Exception exception) {
            log.warn("Python Agent预测展示数据格式无效: {}", exception.getMessage());
            return null;
        }
    }

    private ExecutionProgress readAgentProgress(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        try {
            return objectMapper.treeToValue(node, ExecutionProgress.class);
        } catch (Exception exception) {
            log.warn("Python Agent进度数据格式无效: {}", exception.getMessage());
            return null;
        }
    }

    private AiIntent agentIntent(String capabilityCode, JsonNode parameters) {
        AiIntent intent = new AiIntent();
        intent.setIntent(AiIntentType.GENERAL_CHAT);
        intent.setTool(AiToolName.NONE);
        intent.setSpeechAct(AiSpeechAct.QUESTION);
        intent.setConfidence(1D);
        if (parameters != null && parameters.isObject()) {
            try {
                objectMapper.readerForUpdating(intent).readValue(parameters);
            } catch (Exception exception) {
                log.debug("Python Agent参数无法映射到兼容意图, capabilityCode={}", capabilityCode);
            }
        }
        return intent;
    }

    private String findText(JsonNode node, String fieldName) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        JsonNode direct = node.get(fieldName);
        if (direct != null && direct.isValueNode() && TextUtils.hasText(direct.asText())) return direct.asText();
        if (node.isContainerNode()) {
            for (JsonNode child : node) {
                String value = findText(child, fieldName);
                if (TextUtils.hasText(value)) return value;
            }
        }
        return null;
    }

    private String explainToolFailure(AiIntent intent, String technicalReason) {
        String reason = TextUtils.hasText(technicalReason) ? technicalReason : "业务执行失败";
        try {
            String prompt = "工具=" + intent.getTool() + "，参数=" + compactJson(intent) + "，真实失败原因=" + reason + "。请用中文向业务用户说明：执行到了业务校验阶段但未成功；准确说明原因；给出一条可操作的下一步。" + "限制在100字以内，不得编造原因，不得声称已经重试或成功。";
            return llmClient.complete("你是天然气预测平台的错误解释助手，只能解释给定的真实错误，不执行任何工具。", prompt, 0.1D);
        } catch (Exception ignored) {
            return "本次操作未成功：" + reason + "。请根据提示补充或修正数据后再试。";
        }
    }

    private AiIntent generalChatIntent(AiIntent source) {
        AiIntent intent = source == null ? new AiIntent() : source;
        intent.setTool(AiToolName.ANALYZE_CURRENT_RESULT);
        intent.setIntent(AiIntentType.GENERAL_CHAT);
        intent.setSpeechAct(AiSpeechAct.QUESTION);
        intent.setRequiresConfirmation(false);
        intent.setConfidence(1D);
        intent.getCandidates().clear();
        return intent;
    }

    /**
     * 智能体页面已经选择的业务范围是可信上下文，不应再要求用户在自然语言中重复。
     * 用户原话仍负责表达动作和预测周期；页面范围负责补充预测类型、地区、行业和客户。
     */
    private AiIntent applyRequestScope(AiChatRequest request, AiIntent intent) {
        if (intent == null) intent = new AiIntent();
        if (TextUtils.hasText(request.getAgentType())) intent.setAgentCode(request.getAgentType());
        Map<String, Object> scope = request.getMetadata();
        if (scope == null || scope.isEmpty()) return intent;
        String regionCode = scopeText(scope, "regionCode");
        String regionName = scopeText(scope, "regionName");
        String industryCode = scopeText(scope, "industryCode");
        String industryName = scopeText(scope, "industryName");
        String customerCode = scopeText(scope, "customerCode");
        String customerName = scopeText(scope, "customerName");
        if (TextUtils.hasText(regionCode)) intent.setRegionCode(regionCode);
        if (TextUtils.hasText(regionName)) intent.setRegionName(regionName);
        if (TextUtils.hasText(industryCode)) intent.setIndustryCode(industryCode);
        if (TextUtils.hasText(industryName)) intent.setIndustryName(industryName);
        if (TextUtils.hasText(customerCode)) intent.setCustomerCode(customerCode);
        if (TextUtils.hasText(customerName)) intent.setCustomerName(customerName);
        return intent;
    }

    private String scopeText(Map<String, Object> scope, String key) {
        Object value = scope.get(key);
        return value == null ? null : String.valueOf(value).trim();
    }

    /** “刚才的训练结果”直接绑定会话中的批次，避免再次让大模型猜客户或行业实体。 */
    private AiIntent applyConversationQueryContext(AiConversationTb conversation, String message, AiIntent parsed) {
        String text = message == null ? "" : message.trim();
        boolean trainingQuery = text.contains("训练") && text.matches(".*(结果|状态|进度|到哪|怎么样|效果|指标).*");
        if (!trainingQuery || !TextUtils.hasText(conversation.getLastTrainBatchNo()))
            return parsed;
        ModelTrainRecordTb record = trainRecordMapper.selectOne(Wrappers.<ModelTrainRecordTb>lambdaQuery().eq(ModelTrainRecordTb::getBatchNo, conversation.getLastTrainBatchNo()).last("limit 1"));
        if (record == null)
            return parsed;
        AiIntent intent = new AiIntent();
        intent.setTool(AiToolName.QUERY_TRAINING);
        intent.setIntent(AiIntentType.QUERY_TRAINING);
        intent.setSpeechAct(AiSpeechAct.QUESTION);
        intent.setConfidence(1D);
        intent.setAgentCode(record.getAgentCode());
        intent.setTrainBatchNo(record.getBatchNo());
        intent.setRegionCode(record.getRegionCode());
        intent.setRegionName(record.getRegionName());
        intent.setIndustryCode(record.getIndustryCode());
        intent.setIndustryName(record.getIndustryName());
        intent.setCustomerCode(record.getCustomerCode());
        intent.setCustomerName(record.getCustomerName());
        intent.setRequiresConfirmation(false);
        return intent;
    }

    public List<AiConversationResponse> list() {
        String userId = String.valueOf(SecurityContextHolder.getUserId());
        return conversationMapper.selectList(Wrappers.<AiConversationTb>lambdaQuery().eq(AiConversationTb::getCreatedBy, userId)
                .and(query -> query.eq(AiConversationTb::getScene, "GENERAL").or().isNull(AiConversationTb::getScene))
                .orderByDesc(AiConversationTb::getUpdatedAt).last("limit 50")).stream()
                .map(item -> toResponse(item, false)).toList();
    }

    public AiConversationResponse detail(String conversationNo) {
        return toResponse(findOwned(conversationNo), true);
    }

    /** 获取当前用户与预测批次唯一绑定的会话；首次打开预测详情时自动创建。 */
    public AiConversationResponse forecastDetail(String forecastBatchNo) {
        return toResponse(requireForecastConversation(forecastBatchNo, null), true);
    }

    public void rename(String conversationNo, String title) {
        AiConversationTb conversation = findOwned(conversationNo);
        conversation.setTitle(title.trim());
        conversation.setUpdatedAt(new Date());
        conversationMapper.updateById(conversation);
    }

    public void delete(String conversationNo) {
        AiConversationTb conversation = findOwned(conversationNo);
        messageMapper.delete(Wrappers.<AiConversationMessageTb>lambdaQuery().eq(AiConversationMessageTb::getConversationId, conversation.getId()));
        conversationMapper.deleteById(conversation.getId());
    }

    public AiTrainingProgressResponse trainingProgress(String batchNo) {
        ModelTrainRecordTb record = trainRecordMapper.selectOne(Wrappers.<ModelTrainRecordTb>lambdaQuery()
                .select(ModelTrainRecordTb::getBatchNo, ModelTrainRecordTb::getStatus, ModelTrainRecordTb::getErrorMessage, ModelTrainRecordTb::getTrainStartDate, ModelTrainRecordTb::getTrainEndDate)
                .eq(ModelTrainRecordTb::getBatchNo, batchNo).last("limit 1"));
        if (record == null)
            throw new BusinessException("训练批次不存在：" + batchNo);
        var task = executionTaskMapper.selectOne(Wrappers.<com.gas.forecast.dao.domain.AgentExecutionTaskTb>lambdaQuery()
                .eq(com.gas.forecast.dao.domain.AgentExecutionTaskTb::getLatestTrainBatchNo, batchNo).orderByDesc(com.gas.forecast.dao.domain.AgentExecutionTaskTb::getUpdatedAt).last("limit 1"));
        if (task != null && "SUCCESS".equals(record.getStatus()) && "FORECAST".equals(task.getTaskType()) && !TextUtils.hasText(task.getLatestForecastBatchNo())) {
            taskForecastContinuationService.continueAfterTraining(batchNo);
            task = executionTaskMapper.selectById(task.getId());
        }
        if (task != null && "FORECAST".equals(task.getTaskType()) && TextUtils.hasText(task.getLatestForecastBatchNo())
                && TextUtils.hasText(task.getConversationNo())) {
            // 兼容修复旧任务：续跑预测成功后，会话仍可能指向续跑前的旧预测批次。
            conversationMapper.update(null, Wrappers.<AiConversationTb>lambdaUpdate()
                    .eq(AiConversationTb::getConversationNo, task.getConversationNo())
                    .ne(AiConversationTb::getLastForecastBatchNo, task.getLatestForecastBatchNo())
                    .set(AiConversationTb::getLastTrainBatchNo, batchNo)
                    .set(AiConversationTb::getLastForecastBatchNo, task.getLatestForecastBatchNo())
                    .set(AiConversationTb::getUpdatedAt, new Date()));
        }
        return new AiTrainingProgressResponse(batchNo, record.getStatus(), safeTrainingMessage(record), task == null ? null : task.getTaskNo(),
                task != null && "FORECAST".equals(task.getTaskType()), task == null ? null : task.getLatestForecastBatchNo());
    }

    public AiChatResponse continueForecast(String conversationNo, String trainBatchNo) {
        AiConversationTb conversation = findOwned(conversationNo);
        ModelTrainRecordTb record = trainRecordMapper.selectOne(Wrappers.<ModelTrainRecordTb>lambdaQuery().eq(ModelTrainRecordTb::getBatchNo, trainBatchNo).last("limit 1"));
        if (record == null)
            throw new BusinessException("训练批次不存在：" + trainBatchNo);
        if (!"SUCCESS".equals(record.getStatus()))
            throw new BusinessException("训练尚未成功，暂不能继续预测");
        if (!java.util.Objects.equals(conversation.getLastTrainBatchNo(), trainBatchNo))
            throw new BusinessException("该训练批次不属于当前会话的最新任务");
        ModelForecastConfigTb pendingForecast = forecastConfigMapper.selectOne(Wrappers.<ModelForecastConfigTb>lambdaQuery().eq(ModelForecastConfigTb::getTrainConfigId, record.getTrainConfigId())
                .isNotNull(ModelForecastConfigTb::getCustomerCode).ne(ModelForecastConfigTb::getCustomerCode, "ALL").eq(ModelForecastConfigTb::getEnabled, 1)
                .orderByDesc(ModelForecastConfigTb::getUpdatedAt).orderByDesc(ModelForecastConfigTb::getId).last("limit 1"));
        if (pendingForecast == null)
            throw new BusinessException("训练已完成，但未找到对应的客户预测配置，请重新指定客户发起预测");
        ModelForecastRecordTb continuedRecord = forecastRecordMapper.selectOne(Wrappers.<ModelForecastRecordTb>lambdaQuery().eq(ModelForecastRecordTb::getForecastId, pendingForecast.getId())
                .ge(record.getCreatedAt() != null, ModelForecastRecordTb::getCreatedAt, record.getCreatedAt()).orderByDesc(ModelForecastRecordTb::getId).last("limit 1"));
        if (continuedRecord != null && TextUtils.hasText(continuedRecord.getForecastBatchNo())) {
            conversation.setLastForecastBatchNo(continuedRecord.getForecastBatchNo());
            conversation.setUpdatedAt(new Date());
            conversationMapper.updateById(conversation);
            return forecastProgress(conversationNo, continuedRecord.getForecastBatchNo());
        }
        AiIntent intent = new AiIntent();
        intent.setIntent(AiIntentType.START_FORECAST);
        intent.setConfidence(1D);
        intent.setAgentCode(record.getAgentCode());
        intent.setRegionCode(pendingForecast.getRegionCode());
        intent.setRegionName(pendingForecast.getRegionName());
        intent.setIndustryCode(pendingForecast.getIndustryCode());
        intent.setIndustryName(pendingForecast.getIndustryName());
        intent.setCustomerCode(pendingForecast.getCustomerCode());
        intent.setCustomerName(pendingForecast.getCustomerName());
        intent.setStartDate(pendingForecast.getForecastStartDate());
        intent.setForecastHorizon(pendingForecast.getForecastHorizon());
        try {
            ExecutionResult execution = startForecast(conversation, intent);
            conversation.setUpdatedAt(new Date());
            conversationMapper.updateById(conversation);
            return respondByUpdatingWorkflow(conversation, execution.status(), execution.answer(), intent, trainBatchNo);
        } catch (BusinessException exception) {
            String answer = explainToolFailure(intent, exception.getMessage());
            conversation.setUpdatedAt(new Date());
            conversationMapper.updateById(conversation);
            return respondByUpdatingWorkflow(conversation, "FAILED", answer, intent, trainBatchNo);
        }
    }

    public AiChatResponse forecastProgress(String conversationNo, String batchNo) {
        AiConversationTb conversation = findOwned(conversationNo);
        if (!java.util.Objects.equals(conversation.getLastForecastBatchNo(), batchNo)) {
            var relatedTask = executionTaskMapper.selectOne(Wrappers.<com.gas.forecast.dao.domain.AgentExecutionTaskTb>lambdaQuery()
                    .eq(com.gas.forecast.dao.domain.AgentExecutionTaskTb::getConversationNo, conversationNo)
                    .eq(com.gas.forecast.dao.domain.AgentExecutionTaskTb::getLatestForecastBatchNo, batchNo)
                    .orderByDesc(com.gas.forecast.dao.domain.AgentExecutionTaskTb::getUpdatedAt).last("limit 1"));
            if (relatedTask == null)
                throw new BusinessException("该预测批次不属于当前会话的最新任务");
            // 自动续跑可能先创建预测批次、后更新会话；以任务与批次关系作为可信依据自愈。
            conversation.setLastForecastBatchNo(batchNo);
            if (TextUtils.hasText(relatedTask.getLatestTrainBatchNo()))
                conversation.setLastTrainBatchNo(relatedTask.getLatestTrainBatchNo());
            conversation.setUpdatedAt(new Date());
            conversationMapper.updateById(conversation);
        }
        ModelForecastRecordTb record = forecastRecordMapper.selectOne(new QueryWrapper<ModelForecastRecordTb>().select("forecast_batch_no", "agent_code", "region_code", "region_name", "industry_code",
                "industry_name", "customer_code", "customer_name", "forecast_horizon", "status", "response_param").eq("forecast_batch_no", batchNo).last("limit 1"));
        if (record == null)
            throw new BusinessException("预测批次不存在：" + batchNo);
        AiIntent intent = forecastIntent(record);
        if (Integer.valueOf(1).equals(record.getStatus())) {
            ExecutionProgress progress = buildProgress("EXECUTING", intent, conversation);
            return new AiChatResponse(conversationNo, "EXECUTING", "预测正在后台执行", intent, List.of(), conversation.getLastTrainBatchNo(), batchNo, progress, null, null, List.of(), null);
        }
        if (Integer.valueOf(3).equals(record.getStatus())) {
            String reason = forecastFailureMessage(record.getResponseParam());
            return respondByUpdatingWorkflow(conversation, "FAILED", "预测未能完成：" + reason, intent, conversation.getLastTrainBatchNo());
        }
        return respondByUpdatingWorkflow(conversation, "COMPLETED", "预测已完成，预测批次：" + batchNo, intent, conversation.getLastTrainBatchNo());
    }

    private AiIntent forecastIntent(ModelForecastRecordTb record) {
        AiIntent intent = new AiIntent();
        intent.setIntent(AiIntentType.START_FORECAST);
        intent.setConfidence(1D);
        intent.setAgentCode(record.getAgentCode());
        intent.setRegionCode(record.getRegionCode());
        intent.setRegionName(record.getRegionName());
        intent.setIndustryCode(record.getIndustryCode());
        intent.setIndustryName(record.getIndustryName());
        intent.setCustomerCode(record.getCustomerCode());
        intent.setCustomerName(record.getCustomerName());
        intent.setForecastHorizon(record.getForecastHorizon());
        return intent;
    }

    private String forecastFailureMessage(byte[] responseParam) {
        if (responseParam == null || responseParam.length == 0)
            return "模型平台没有返回具体原因，请稍后重试；如持续失败，请将预测批次号提供给管理员。";
        String reason = new String(responseParam, java.nio.charset.StandardCharsets.UTF_8).trim();
        if (reason.contains("未来天气") || reason.contains("weather"))
            return "缺少预测日期范围内的未来天气特征，请先同步天气预报数据后重试。";
        if (reason.contains("insufficient") || reason.contains("数据不足"))
            return "预测所需的特征数据不足，请检查预测日期范围内的数据完整性。";
        return reason.length() > 300 ? reason.substring(0, 300) + "…" : reason;
    }

    private String safeTrainingMessage(ModelTrainRecordTb record) {
        if (!"FAILED".equals(record.getStatus()))
            return null;
        String error = record.getErrorMessage();
        if (TextUtils.hasText(error)) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("至少需要?(\\d+)天|至少需要\\s*(\\d+)\\s*条").matcher(error);
            if (matcher.find()) {
                String required = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
                return "暂时无法完成训练：有效历史数据不足，模型至少需要" + required + "天数据。请补充数据后重试。";
            }
            if (error.matches(".*(insufficient data|数据不足|连续.*数据).*"))
                return "暂时无法完成训练：历史数据不足或不连续，请检查销量数据完整性后重试。";
        }
        return "本次训练未能完成，请稍后重试；如持续失败，请将批次号提供给平台管理员。";
    }

    private AiIntent resolveIntent(AiConversationTb conversation, String message) {
        List<Map<String, String>> recentHistory = recentHistory(conversation.getId(), 12);
        if (!TextUtils.hasText(conversation.getPendingIntentJson()) || intentParser.startsNewQuestion(message)) {
            conversation.setPendingIntentJson(null);
            conversation.setStatus("ACTIVE");
            return intentParser.parse(message, recentHistory);
        }
        try {
            AiIntent pending = objectMapper.readValue(conversation.getPendingIntentJson(), AiIntent.class);
            toolRegistry.normalize(pending);
            if (isCancel(message)) {
                pending.setSpeechAct(AiSpeechAct.CANCEL);
                pending.setRequiresConfirmation(false);
                pending.setConfidence(1D);
                return pending;
            }
            if (isConfirm(message) && executionPolicy.isMutation(pending.getIntent())) {
                pending.setSpeechAct(AiSpeechAct.CONFIRM);
                pending.setRequiresConfirmation(false);
                pending.setConfidence(1D);
                return pending;
            }
            if (!pending.getCandidates().isEmpty() && entityTranslator.applyCandidate(pending, message)) {
                pending.setConfidence(1D);
                return pending;
            }
            return intentParser.continueIntent(pending, message);
        } catch (Exception exception) {
            conversation.setPendingIntentJson(null);
            return intentParser.parse(message, recentHistory);
        }
    }

    /** 当前消息已先入库，因此取它之前最多六轮（十二条）消息。 */
    private List<Map<String, String>> recentHistory(Long conversationId, int limit) {
        List<AiConversationMessageTb> stored = messages(conversationId);
        int currentIndex = Math.max(0, stored.size() - 1);
        int fromIndex = Math.max(0, currentIndex - limit);
        return stored.subList(fromIndex, currentIndex).stream()
                .filter(item -> TextUtils.hasText(item.getRole()) && TextUtils.hasText(item.getContent()))
                .map(item -> Map.of("role", item.getRole(), "content", item.getContent()))
                .toList();
    }

    private AiChatResponse answerWithoutExecution(AiConversationTb conversation, AiIntent intent) {
        if (intent.getIntent() == AiIntentType.START_FORECAST && TextUtils.hasText(intent.getCustomerName())) {
            intent.setIntent(AiIntentType.QUERY_CAPABILITY);
            intent.setSpeechAct(AiSpeechAct.QUESTION);
            AiEntityTranslator.TranslationResult translation = entityTranslator.translate(intent);
            if (!translation.resolved()) {
                if (translation.confirmationRequired())
                    return confirm(conversation, intent, replyComposer.compose("NEED_SELECTION", withCandidates(translation.message(), intent), intent));
                return needInformation(conversation, intent, replyComposer.compose("NEED_INFORMATION", translation.message(), intent));
            }
            ExecutionResult result = queryCapability(intent);
            return respond(conversation, result.status(), result.answer(), intent);
        }
        String action = Boolean.TRUE.equals(intent.getForceRetrain()) ? "重新训练并在训练成功后预测" : intent.getIntent() == AiIntentType.START_TRAINING ? "训练" : "预测";
        return respond(conversation, "COMPLETED", "你当前是在询问“" + action + "”相关信息，并未明确要求执行，因此本次不会发起任何任务。若需要执行，请明确说“帮我" + action + "某客户”。", intent);
    }

    private String executionConfirmationMessage(AiIntent intent) {
        String action = intent.getIntent() == AiIntentType.START_TRAINING ? "训练" : "预测";
        if (Boolean.TRUE.equals(intent.getBulkScope())) {
            String scope = TextUtils.hasText(intent.getRegionName()) ? intent.getRegionName() : "";
            if (TextUtils.hasText(intent.getIndustryName()))
                scope += (TextUtils.hasText(scope) ? " / " : "") + intent.getIndustryName() + "行业";
            if (!TextUtils.hasText(scope))
                scope = "当前范围";
            return "已识别为批量操作：准备对“" + scope + "”内的全部客户逐一执行" + action + "。这可能创建多个后台批次，请回复“确认执行”后继续，或回复“取消”。";
        }
        String target = TextUtils.hasText(intent.getCustomerName()) ? "客户“" + intent.getCustomerName() + "”" : "当前范围";
        return "我理解你可能希望为" + target + "执行" + action + "，但表达还不够明确。本次尚未创建任务。请回复“确认执行”后再继续，或回复“取消”。";
    }

    private boolean isConfirm(String message) {
        String text = message == null ? "" : message.trim();
        return text.matches("^(确认|确认执行|执行|继续执行|是的|好的|可以)$");
    }

    private ExecutionResult execute(AiConversationTb conversation, AiIntent intent) {
        return switch (intent.getIntent()) {
            case START_TRAINING -> startTraining(conversation, intent);
            case QUERY_TRAINING -> queryTraining(conversation, intent);
            case START_FORECAST -> startForecast(conversation, intent);
            case QUERY_FORECAST, QUERY_RESULT -> queryForecast(conversation, intent);
            case QUERY_CAPABILITY -> queryCapability(intent);
            case QUERY_DATA -> queryCustomerData(intent);
            case GENERAL_CHAT -> generalChat(conversation, intent);
            default -> new ExecutionResult("NEED_INFORMATION", "请说明需要训练、预测还是查询任务状态。");
        };
    }

    private ExecutionResult queryCustomerData(AiIntent intent) {
        if ("DATA_COUNT".equalsIgnoreCase(intent.getSortBy())) {
            return queryCustomerDataByVolume(intent);
        }
        var countQuery = Wrappers.<BaseCustomerTb>lambdaQuery();
        if (TextUtils.hasText(intent.getRegionCode()))
            countQuery.eq(BaseCustomerTb::getRegionCode, intent.getRegionCode());
        if (TextUtils.hasText(intent.getIndustryCode()))
            countQuery.eq(BaseCustomerTb::getIndustryCode, intent.getIndustryCode());
        long total = customerMapper.selectCount(countQuery);
        var query = Wrappers.<BaseCustomerTb>lambdaQuery();
        if (TextUtils.hasText(intent.getRegionCode()))
            query.eq(BaseCustomerTb::getRegionCode, intent.getRegionCode());
        if (TextUtils.hasText(intent.getIndustryCode()))
            query.eq(BaseCustomerTb::getIndustryCode, intent.getIndustryCode());
        query.orderByAsc(BaseCustomerTb::getIndustryName).orderByAsc(BaseCustomerTb::getCustomerName);
        List<BaseCustomerTb> customers = customerMapper.selectList(query.last("limit 20"));
        if (customers.isEmpty())
            return new ExecutionResult("COMPLETED", "没有查询到符合条件的客户信息。");
        String scope = TextUtils.hasText(intent.getRegionName()) ? intent.getRegionName() : "当前范围";
        if (TextUtils.hasText(intent.getIndustryName()))
            scope += " / " + intent.getIndustryName() + "行业";
        StringBuilder answer = new StringBuilder("查询到").append(scope).append("共有 ").append(total).append(" 个客户，当前显示前 ").append(customers.size()).append(" 个：");
        int index = 1;
        for (BaseCustomerTb customer : customers) {
            answer.append("\n").append(index++).append(". ").append(customer.getCustomerName()).append("（").append(nullToDash(customer.getIndustryName())).append("，编码：")
                    .append(customer.getCustomerCode()).append("）");
        }
        if (total > customers.size())
            answer.append("\n\n结果较多，请继续指定城市、行业或更完整的客户名称缩小范围。");
        return new ExecutionResult("COMPLETED", answer.toString());
    }

    /** 按有效销量日期数统计客户数据量，避免重复日期把客户排名放大。 */
    private ExecutionResult queryCustomerDataByVolume(AiIntent intent) {
        QueryWrapper<DataDailySalesTb> query = Wrappers.query();
        query.select("customer_code", "MAX(customer_name) AS customer_name", "MAX(industry_name) AS industry_name", "COUNT(DISTINCT stat_date) AS data_count", "MIN(stat_date) AS start_date",
                "MAX(stat_date) AS end_date").isNotNull("customer_code").ne("customer_code", "").isNotNull("gas_sales");
        if (TextUtils.hasText(intent.getRegionCode()))
            query.eq("region_code", intent.getRegionCode());
        if (TextUtils.hasText(intent.getIndustryCode()))
            query.eq("industry_code", intent.getIndustryCode());
        query.groupBy("customer_code");
        if ("ASC".equalsIgnoreCase(intent.getSortDirection())) {
            query.orderByAsc("data_count").orderByAsc("customer_name");
        } else {
            query.orderByDesc("data_count").orderByAsc("customer_name");
        }

        List<Map<String, Object>> ranked = dailySalesMapper.selectMaps(query);
        if (ranked.isEmpty())
            return new ExecutionResult("COMPLETED", "没有查询到符合条件且包含有效销量的客户数据。");

        int limit = intent.getLimit() == null ? 20 : Math.min(50, Math.max(1, intent.getLimit()));
        int shown = Math.min(limit, ranked.size());
        String scope = TextUtils.hasText(intent.getRegionName()) ? intent.getRegionName() : "当前范围";
        if (TextUtils.hasText(intent.getIndustryName()))
            scope += " / " + intent.getIndustryName() + "行业";
        String direction = "ASC".equalsIgnoreCase(intent.getSortDirection()) ? "从少到多" : "从多到少";
        StringBuilder answer = new StringBuilder("查询到").append(scope).append("共有 ").append(ranked.size()).append(" 个有销量数据的客户，按有效销量日数").append(direction).append("排序，显示前 ").append(shown).append(" 个：");
        for (int i = 0; i < shown; i++) {
            Map<String, Object> row = ranked.get(i);
            answer.append("\n").append(i + 1).append(". ").append(mapValue(row, "customer_name")).append("：").append(mapValue(row, "data_count")).append(" 天").append("（")
                    .append(mapValue(row, "start_date")).append(" 至 ").append(mapValue(row, "end_date")).append("，编码：").append(mapValue(row, "customer_code")).append("）");
        }
        return new ExecutionResult("COMPLETED", answer.toString());
    }

    private String mapValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null)
            value = row.get(key.toUpperCase());
        return value == null ? "-" : String.valueOf(value);
    }

    private ExecutionResult queryCapability(AiIntent intent) {
        String agentCode = TextUtils.hasText(intent.getAgentCode()) ? intent.getAgentCode() : "short-term";
        List<ModelConfigTb> models = modelConfigMapper.selectList(Wrappers.<ModelConfigTb>lambdaQuery().eq(ModelConfigTb::getAgentCode, agentCode).orderByAsc(ModelConfigTb::getId));
        if (models.isEmpty())
            return new ExecutionResult("COMPLETED", "当前没有配置可用的" + agentName(agentCode) + "模型。");

        List<BaseCustomerTb> customers = customerMapper
                .selectList(Wrappers.<BaseCustomerTb>lambdaQuery().orderByAsc(BaseCustomerTb::getRegionName).orderByAsc(BaseCustomerTb::getIndustryName).orderByAsc(BaseCustomerTb::getCustomerName));
        Map<String, BaseCustomerTb> supported = new LinkedHashMap<>();
        for (ModelConfigTb model : models) {
            List<ModelConfigScopeTb> scopes = modelScopeMapper.selectList(Wrappers.<ModelConfigScopeTb>lambdaQuery().eq(ModelConfigScopeTb::getModelId, model.getId()));
            for (BaseCustomerTb customer : customers) {
                boolean matched = scopes.isEmpty() || scopes.stream().anyMatch(scope -> scopeMatches(scope, customer));
                if (matched && (!TextUtils.hasText(intent.getRegionCode()) || intent.getRegionCode().equals(customer.getRegionCode()))
                        && (!TextUtils.hasText(intent.getIndustryCode()) || intent.getIndustryCode().equals(customer.getIndustryCode()))
                        && (!TextUtils.hasText(intent.getCustomerCode()) || intent.getCustomerCode().equals(customer.getCustomerCode()))) {
                    supported.putIfAbsent(customer.getCustomerCode(), customer);
                }
            }
        }
        if (supported.isEmpty() && TextUtils.hasText(intent.getCustomerName()))
            return new ExecutionResult("COMPLETED", "客户“" + intent.getCustomerName() + "”目前不在" + agentName(agentCode) + "的模型支持范围内，暂不能预测。本次仅进行了能力查询，没有发起训练或预测任务。");
        if (supported.isEmpty())
            return new ExecutionResult("COMPLETED", agentName(agentCode) + "当前配置范围内没有匹配的客户。");
        if (TextUtils.hasText(intent.getCustomerCode())) {
            BaseCustomerTb customer = supported.values().iterator().next();
            return new ExecutionResult("COMPLETED", "客户“" + customer.getCustomerName() + "”（" + nullToDash(customer.getRegionName()) + " / " + nullToDash(customer.getIndustryName()) + "）在"
                    + agentName(agentCode) + "的模型支持范围内，可以预测。本次仅进行了能力查询，没有发起训练或预测任务。");
        }
        StringBuilder answer = new StringBuilder("当前").append(agentName(agentCode)).append("支持 ").append(supported.size()).append(" 个客户，当前显示前 ").append(Math.min(20, supported.size())).append(" 个：");
        int index = 1;
        for (BaseCustomerTb customer : supported.values()) {
            if (index > 20)
                break;
            answer.append("\n").append(index++).append(". ").append(customer.getCustomerName()).append("（").append(nullToDash(customer.getRegionName())).append(" / ")
                    .append(nullToDash(customer.getIndustryName())).append("）");
        }
        if (supported.size() > 20)
            answer.append("\n\n结果较多，请指定地区、行业或客户名称进一步筛选。");
        return new ExecutionResult("COMPLETED", answer.toString());
    }

    private boolean scopeMatches(ModelConfigScopeTb scope, BaseCustomerTb customer) {
        boolean regionMatches = !TextUtils.hasText(scope.getRegionCode()) || scope.getRegionCode().equals(customer.getRegionCode());
        boolean industryMatches = !TextUtils.hasText(scope.getIndustryCode()) || scope.getIndustryCode().equals(customer.getIndustryCode());
        if (!regionMatches || !industryMatches)
            return false;
        if (!TextUtils.hasText(scope.getCustomerCode()) || scope.getCustomerCode().equals(customer.getCustomerCode()))
            return true;
        // 已明确配置行业范围时，客户项只决定优先级，不把同行业其他客户排除在能力范围之外。
        return TextUtils.hasText(scope.getIndustryCode());
    }

    private String agentName(String agentCode) {
        return switch (agentCode) {
            case "monthly-sales" -> "月度消费智能体";
            case "winter-supply" -> "冬季保供智能体";
            default -> "短期预测智能体";
        };
    }

    private String nullToDash(String value) {
        return TextUtils.hasText(value) ? value : "未配置";
    }

    private ExecutionResult startTraining(AiConversationTb conversation, AiIntent intent) {
        if (Boolean.TRUE.equals(intent.getBulkScope()) && !TextUtils.hasText(intent.getCustomerCode()))
            return startBulkTraining(intent);
        AutoTrainRequest request = new AutoTrainRequest();
        request.setAgentCode(intent.getAgentCode());
        request.setRegionCode(intent.getRegionCode());
        request.setIndustryCode(intent.getIndustryCode());
        request.setCustomerCode(intent.getCustomerCode());
        applyTrainingRange(request, intent);
        request.setTriggerSource("AI");
        var result = autoTrainService.execute(request);
        conversation.setLastTrainBatchNo(result.getTrainBatchNo());
        conversation.setLastForecastBatchNo(null);
        return new ExecutionResult("EXECUTING", "已为" + displayScope(intent) + "发起训练（" + trainingRangeLabel(intent) + "），训练批次：" + result.getTrainBatchNo() + "，当前状态：" + result.getStatus()
                + "。你可以继续问“这个训练到哪了”。");
    }

    private ExecutionResult startBulkTraining(AiIntent intent) {
        var query = Wrappers.<BaseCustomerTb>lambdaQuery();
        if (TextUtils.hasText(intent.getRegionCode()))
            query.eq(BaseCustomerTb::getRegionCode, intent.getRegionCode());
        if (TextUtils.hasText(intent.getIndustryCode()))
            query.eq(BaseCustomerTb::getIndustryCode, intent.getIndustryCode());
        List<BaseCustomerTb> customers = customerMapper.selectList(query.orderByAsc(BaseCustomerTb::getCustomerName));
        if (customers.isEmpty())
            return new ExecutionResult("COMPLETED", "当前范围内没有找到可训练客户，本次未创建任务。");
        int submitted = 0;
        int failed = 0;
        List<String> failures = new java.util.ArrayList<>();
        for (BaseCustomerTb customer : customers) {
            AutoTrainRequest request = new AutoTrainRequest();
            request.setAgentCode(intent.getAgentCode());
            request.setCustomerCode(customer.getCustomerCode());
            applyTrainingRange(request, intent);
            request.setForceTrain(Boolean.TRUE.equals(intent.getForceRetrain()));
            request.setTriggerSource("AI");
            try {
                autoTrainService.execute(request);
                submitted++;
            } catch (RuntimeException exception) {
                failed++;
                if (failures.size() < 5)
                    failures.add(customer.getCustomerName() + "：" + exception.getMessage());
            }
        }
        StringBuilder answer = new StringBuilder("批量训练任务已受理：范围内共 ").append(customers.size()).append(" 个客户，成功创建 ").append(submitted).append(" 个，创建失败 ").append(failed).append(" 个。");
        if (!failures.isEmpty())
            answer.append("\n部分失败原因：\n- ").append(String.join("\n- ", failures));
        return new ExecutionResult(failed == customers.size() ? "FAILED" : "EXECUTING", answer.toString());
    }

    private ExecutionResult queryTraining(AiConversationTb conversation, AiIntent intent) {
        String batchNo = TextUtils.hasText(intent.getTrainBatchNo()) ? intent.getTrainBatchNo() : conversation.getLastTrainBatchNo();
        if (!TextUtils.hasText(batchNo))
            return new ExecutionResult("NEED_INFORMATION", "请提供训练批次号，或者先在当前会话中发起训练。");
        ModelTrainResultRequest request = new ModelTrainResultRequest();
        request.setBatchNo(batchNo);
        var result = trainExecutionService.getTrainResult(request);
        if (result.getBatches() == null || result.getBatches().isEmpty())
            return new ExecutionResult("NEED_INFORMATION", "没有找到训练批次 " + batchNo + "。请核对批次号。");
        var batch = result.getBatches().get(0);
        StringBuilder answer = new StringBuilder("训练批次 ").append(batchNo).append(" 当前状态：").append(batch.getStatus()).append("。");
        if (TextUtils.hasText(batch.getMessage()))
            answer.append("\n\n").append(batch.getMessage());
        if ("SUCCESS".equals(batch.getStatus())) {
            if (TextUtils.hasText(batch.getBestModel()))
                answer.append("\n使用模型：").append(batch.getBestModel()).append("。");
            answer.append("\n训练范围：").append(nullToDash(batch.getTrainStartDate())).append(" 至 ").append(nullToDash(batch.getTrainEndDate())).append("。");
            answer.append("\n评估指标：MAPE ").append(metricText(batch.getMape(), "%")).append("，WMAPE ").append(metricText(batch.getWmape(), "%")).append("，SMAPE ")
                    .append(metricText(batch.getSmape(), "%")).append("，RMSE ").append(metricText(batch.getRmse(), "")).append("，MAE ").append(metricText(batch.getMae(), "")).append("，R² ")
                    .append(metricText(batch.getR2(), "")).append("。");
            if (batch.getMape() == null && batch.getWmape() == null && batch.getSmape() == null && batch.getR2() == null && isZero(batch.getRmse()) && isZero(batch.getMae())) {
                answer.append("\n本批次回测实际销量全部为0，百分比误差和R²无法计算；RMSE、MAE为0不代表模型有效。");
            }
        }
        return new ExecutionResult("COMPLETED", answer.toString());
    }

    private String metricText(BigDecimal value, String suffix) {
        return value == null ? "不可计算" : value.stripTrailingZeros().toPlainString() + suffix;
    }

    private boolean isZero(BigDecimal value) {
        return value != null && BigDecimal.ZERO.compareTo(value) == 0;
    }

    private ExecutionResult startForecast(AiConversationTb conversation, AiIntent intent) {
        if (Boolean.TRUE.equals(intent.getBulkScope()) && !TextUtils.hasText(intent.getCustomerCode())) {
            return startBulkForecast(conversation, intent);
        }
        // 当前会话切换客户时，旧客户的预测批次不能继续作为本次消息的结果或查询上下文。
        if (TextUtils.hasText(conversation.getLastForecastBatchNo())) {
            ModelForecastRecordTb previousForecast = forecastRecordMapper.selectOne(Wrappers.<ModelForecastRecordTb>lambdaQuery().select(ModelForecastRecordTb::getCustomerCode)
                    .eq(ModelForecastRecordTb::getForecastBatchNo, conversation.getLastForecastBatchNo()).last("limit 1"));
            if (previousForecast == null || !java.util.Objects.equals(previousForecast.getCustomerCode(), intent.getCustomerCode())) {
                conversation.setLastForecastBatchNo(null);
            }
        }
        // 客户预测必须使用该客户自己的销量与天气训练结果。
        ModelTrainConfigTb trainConfig = trainConfigMapper.selectOne(Wrappers.<ModelTrainConfigTb>lambdaQuery().eq(ModelTrainConfigTb::getAgentCode, intent.getAgentCode())
                .eq(ModelTrainConfigTb::getRegionCode, intent.getRegionCode()).eq(ModelTrainConfigTb::getIndustryCode, intent.getIndustryCode())
                .eq(ModelTrainConfigTb::getCustomerCode, intent.getCustomerCode())
                .eq(ModelTrainConfigTb::getEnabled, 1).orderByDesc(ModelTrainConfigTb::getUpdatedAt).orderByDesc(ModelTrainConfigTb::getId).last("limit 1"));
        ModelTrainRecordTb trained = trainConfig == null
                ? null
                : trainRecordMapper.selectOne(Wrappers.<ModelTrainRecordTb>lambdaQuery().eq(ModelTrainRecordTb::getTrainConfigId, trainConfig.getId()).eq(ModelTrainRecordTb::getStatus, "SUCCESS")
                        .orderByDesc(ModelTrainRecordTb::getUpdatedAt).orderByDesc(ModelTrainRecordTb::getId).last("limit 1"));
        if (Boolean.TRUE.equals(intent.getForceRetrain())) {
            ExecutionResult training = startTrainingForForecast(conversation, intent);
            return new ExecutionResult("WAITING_TRAINING", "已按要求为该客户重新训练。\n" + training.answer() + "\n训练成功后系统会自动继续预测，无需再次提交。");
        }
        boolean rangeMismatch = trained != null && !matchesRequestedTrainingRange(trainConfig, intent);
        boolean staleTraining = trained != null && isTrainingStale(trained, trainConfig, intent);
        if (rangeMismatch || staleTraining) {
            ExecutionResult training = startTrainingForForecast(conversation, intent);
            String reason = rangeMismatch ? "现有成功模型的训练数据范围不符合本次要求（" + trainingRangeLabel(intent) + "）" : "销量数据已更新，现有模型尚未训练到最新数据";
            return new ExecutionResult("WAITING_TRAINING", reason + "，已自动重新训练。\n" + training.answer()
                    + "\n训练成功后系统会自动继续预测，无需再次提交。");
        }
        if (trainConfig == null) {
            try {
                ExecutionResult training = startTrainingForForecast(conversation, intent);
                return new ExecutionResult("WAITING_TRAINING", "已识别客户“" + intent.getCustomerName() + "”（编码：" + intent.getCustomerCode() + "）。该客户尚无可用训练结果，已先自动发起训练。\n" + training.answer());
            } catch (BusinessException exception) {
                if (exception.getMessage() != null && exception.getMessage().contains("没有找到支持当前业务范围的模型")) {
                    return unsupportedCustomer(intent);
                }
                throw exception;
            }
        }
        if (trained == null) {
            ExecutionResult training = startTrainingForForecast(conversation, intent);
            return new ExecutionResult("WAITING_TRAINING", "该客户没有可用的成功模型，已先发起客户训练。\n" + training.answer() + "\n训练成功后系统会自动继续该客户的预测，无需再次提交。");
        }
        conversation.setLastTrainBatchNo(trained.getBatchNo());
        int horizon = intent.getForecastHorizon() == null ? 30 : Math.max(1, Math.min(intent.getForecastHorizon(), 366));
        String minimumStartDate = nextFeatureDate(trainConfig);
        String startDate = normalizeForecastStartDate(intent.getStartDate(), minimumStartDate);
        ModelForecastConfigTb existing = forecastConfigMapper.selectOne(Wrappers.<ModelForecastConfigTb>lambdaQuery().eq(ModelForecastConfigTb::getTrainConfigId, trainConfig.getId())
                .eq(ModelForecastConfigTb::getCustomerCode, intent.getCustomerCode()).eq(ModelForecastConfigTb::getEnabled, 1).orderByDesc(ModelForecastConfigTb::getUpdatedAt).last("limit 1"));
        ModelForecastConfigSaveRequest config = new ModelForecastConfigSaveRequest();
        config.setId(existing == null ? null : existing.getId());
        config.setForecastName(intent.getCustomerName() + "未来" + horizon + "天预测");
        config.setForecastStartDate(startDate);
        config.setForecastHorizon(horizon);
        config.setForecastFrequency("DAILY");
        config.setTrainConfigCode(trainConfig.getTrainCode());
        config.setCustomerCode(intent.getCustomerCode());
        config.setEnabled(1);
        config.setRemark("AI对话发起");
        ModelForecastConfigTb saved = forecastService.saveConfig(config);
        ModelForecastExecuteRequest executeRequest = new ModelForecastExecuteRequest();
        executeRequest.setForecastId(saved.getId());
        executeRequest.setForecastStartDate(startDate);
        executeRequest.setForecastHorizon(horizon);
        executeRequest.setForecastFrequency("DAILY");
        var result = forecastService.submit(executeRequest);
        conversation.setLastForecastBatchNo(result.getForecastBatchNo());
        return new ExecutionResult("EXECUTING", "已提交" + displayScope(intent) + "的未来" + horizon + "天预测，预测批次：" + result.getForecastBatchNo() + "。预测在后台执行，页面会自动更新进度。");
    }

    private ExecutionResult startBulkForecast(AiConversationTb conversation, AiIntent intent) {
        var query = Wrappers.<BaseCustomerTb>lambdaQuery();
        if (TextUtils.hasText(intent.getRegionCode()))
            query.eq(BaseCustomerTb::getRegionCode, intent.getRegionCode());
        if (TextUtils.hasText(intent.getIndustryCode()))
            query.eq(BaseCustomerTb::getIndustryCode, intent.getIndustryCode());
        query.orderByAsc(BaseCustomerTb::getCustomerName);
        List<BaseCustomerTb> customers = customerMapper.selectList(query);
        if (customers.isEmpty())
            return new ExecutionResult("COMPLETED", "当前范围内没有找到可预测客户，本次未创建任务。");
        String previousTrainBatchNo = conversation.getLastTrainBatchNo();
        String previousForecastBatchNo = conversation.getLastForecastBatchNo();
        int submitted = 0;
        int training = 0;
        int forecasting = 0;
        int failed = 0;
        List<String> failures = new java.util.ArrayList<>();
        List<String> taskSamples = new java.util.ArrayList<>();
        List<BulkTask> bulkTasks = new java.util.ArrayList<>();
        Date bulkStartedAt = new Date();
        for (BaseCustomerTb customer : customers) {
            AiIntent child = new AiIntent();
            child.setIntent(AiIntentType.START_FORECAST);
            child.setTool(AiToolName.START_FORECAST);
            child.setSpeechAct(AiSpeechAct.COMMAND);
            child.setConfidence(1D);
            child.setAgentCode(intent.getAgentCode());
            child.setRegionCode(customer.getRegionCode());
            child.setRegionName(customer.getRegionName());
            child.setIndustryCode(customer.getIndustryCode());
            child.setIndustryName(customer.getIndustryName());
            child.setCustomerCode(customer.getCustomerCode());
            child.setCustomerName(customer.getCustomerName());
            child.setForecastHorizon(intent.getForecastHorizon());
            child.setTrainMode(intent.getTrainMode());
            child.setTrainingPeriods(intent.getTrainingPeriods());
            child.setTrainingStartDate(intent.getTrainingStartDate());
            child.setTrainingEndDate(intent.getTrainingEndDate());
            child.setForceRetrain(intent.getForceRetrain());
            try {
                ExecutionResult result = startForecast(conversation, child);
                submitted++;
                if ("WAITING_TRAINING".equals(result.status())) {
                    training++;
                    bulkTasks.add(new BulkTask(customer.getCustomerCode(), customer.getCustomerName(), conversation.getLastTrainBatchNo(), null));
                    if (taskSamples.size() < 10)
                        taskSamples.add(customer.getCustomerName() + "：训练 " + conversation.getLastTrainBatchNo());
                } else {
                    forecasting++;
                    bulkTasks.add(new BulkTask(customer.getCustomerCode(), customer.getCustomerName(), null, conversation.getLastForecastBatchNo()));
                    if (taskSamples.size() < 10)
                        taskSamples.add(customer.getCustomerName() + "：预测 " + conversation.getLastForecastBatchNo());
                }
            } catch (RuntimeException exception) {
                failed++;
                if (failures.size() < 5)
                    failures.add(customer.getCustomerName() + "：" + exception.getMessage());
            }
        }
        // 批量任务包含多个批次，不能用最后一个客户覆盖当前会话的单批次上下文。
        conversation.setLastTrainBatchNo(previousTrainBatchNo);
        conversation.setLastForecastBatchNo(previousForecastBatchNo);
        StringBuilder answer = new StringBuilder("批量预测任务已受理：范围内共 ").append(customers.size()).append(" 个客户，已直接提交预测 ").append(forecasting).append(" 个，先进入训练 ").append(training).append(" 个，创建失败 ")
                .append(failed).append(" 个。").append("\n这些是多个独立后台批次，不是只执行一个客户。");
        if (!taskSamples.isEmpty())
            answer.append("\n\n部分任务（前 ").append(taskSamples.size()).append(" 条）：\n- ").append(String.join("\n- ", taskSamples));
        if (submitted > taskSamples.size())
            answer.append("\n- 其余 ").append(submitted - taskSamples.size()).append(" 个请在训练批次/预测记录页面查看。");
        if (!failures.isEmpty())
            answer.append("\n部分失败原因：\n- ").append(String.join("\n- ", failures));
        return new ExecutionResult(failed == customers.size() ? "FAILED" : "EXECUTING", answer.toString(), new BulkExecution(bulkStartedAt, bulkTasks));
    }

    private ExecutionResult startTrainingForForecast(AiConversationTb conversation, AiIntent intent) {
        AutoTrainRequest request = new AutoTrainRequest();
        request.setAgentCode(intent.getAgentCode());
        request.setRegionCode(intent.getRegionCode());
        request.setIndustryCode(intent.getIndustryCode());
        request.setCustomerCode(intent.getCustomerCode());
        // 训练范围使用独立字段，不能把预测日期和预测天数当作训练区间。
        applyTrainingRange(request, intent);
        request.setForceTrain(Boolean.TRUE.equals(intent.getForceRetrain()));
        request.setTriggerSource("AI");
        var result = autoTrainService.execute(request);
        conversation.setLastTrainBatchNo(result.getTrainBatchNo());
        ModelTrainConfigTb trainConfig = trainConfigMapper.selectById(result.getTrainConfigId());
        if (trainConfig != null) {
            int horizon = intent.getForecastHorizon() == null ? 30 : Math.max(1, Math.min(intent.getForecastHorizon(), 366));
            ModelForecastConfigSaveRequest pending = new ModelForecastConfigSaveRequest();
            pending.setForecastName(intent.getCustomerName() + "未来" + horizon + "天预测");
            pending.setForecastStartDate(normalizeForecastStartDate(intent.getStartDate(), nextFeatureDate(trainConfig)));
            pending.setForecastHorizon(horizon);
            pending.setForecastFrequency("DAILY");
            pending.setTrainConfigCode(trainConfig.getTrainCode());
            pending.setCustomerCode(intent.getCustomerCode());
            pending.setEnabled(1);
            pending.setRemark("AI对话等待训练后自动预测");
            forecastService.saveConfig(pending);
        }
        return new ExecutionResult("EXECUTING", "已为客户“" + intent.getCustomerName() + "”发起训练，训练批次：" + result.getTrainBatchNo() + "，当前状态：" + result.getStatus() + "。");
    }

    private void applyTrainingRange(AutoTrainRequest request, AiIntent intent) {
        request.setTrainMode(intent.getTrainMode());
        request.setRecentPeriods(intent.getTrainingPeriods());
        request.setTrainStartDate(intent.getTrainingStartDate());
        request.setTrainEndDate(intent.getTrainingEndDate());
    }

    /** 仅在用户明确指定训练窗口时检查；没有指定时沿用现有成功模型。 */
    private boolean matchesRequestedTrainingRange(ModelTrainConfigTb config, AiIntent intent) {
        if (!TextUtils.hasText(intent.getTrainMode()))
            return true;
        if (!intent.getTrainMode().equalsIgnoreCase(config.getTrainMode()))
            return false;
        if ("RECENT".equalsIgnoreCase(intent.getTrainMode()))
            return java.util.Objects.equals(intent.getTrainingPeriods(), config.getRecentPeriods());
        if ("RANGE".equalsIgnoreCase(intent.getTrainMode()))
            return java.util.Objects.equals(intent.getTrainingStartDate(), config.getTrainStartDate())
                    && java.util.Objects.equals(intent.getTrainingEndDate(), config.getTrainEndDate());
        return true;
    }

    private boolean isTrainingStale(ModelTrainRecordTb trained, ModelTrainConfigTb config, AiIntent intent) {
        // 固定日期区间是用户有意冻结的数据范围，不因区间外新增数据而重训。
        if ("RANGE".equalsIgnoreCase(config.getTrainMode()) || "RANGE".equalsIgnoreCase(intent.getTrainMode()))
            return false;
        DataDailySalesTb latest = dailySalesMapper.selectOne(Wrappers.<DataDailySalesTb>lambdaQuery().select(DataDailySalesTb::getStatDate, DataDailySalesTb::getCreatedAt)
                .eq(DataDailySalesTb::getCustomerCode, intent.getCustomerCode()).isNotNull(DataDailySalesTb::getGasSales)
                .orderByDesc(DataDailySalesTb::getStatDate).last("limit 1"));
        if (latest == null)
            return false;
        if (TextUtils.hasText(trained.getTrainEndDate()))
            return TextUtils.hasText(latest.getStatDate()) && latest.getStatDate().compareTo(trained.getTrainEndDate()) > 0;
        // RECENT/ALL 配置本身不保存固定结束日期。不能把空结束日期直接当成“模型过期”，
        // 否则训练成功后的自动预测会再次触发训练，形成重复批次。
        DataDailySalesTb latestImported = dailySalesMapper.selectOne(Wrappers.<DataDailySalesTb>lambdaQuery().select(DataDailySalesTb::getCreatedAt)
                .eq(DataDailySalesTb::getCustomerCode, intent.getCustomerCode()).isNotNull(DataDailySalesTb::getGasSales)
                .orderByDesc(DataDailySalesTb::getCreatedAt).last("limit 1"));
        return latestImported != null && latestImported.getCreatedAt() != null && trained.getFinishedAt() != null && latestImported.getCreatedAt().after(trained.getFinishedAt());
    }

    private String trainingRangeLabel(AiIntent intent) {
        if ("ALL".equalsIgnoreCase(intent.getTrainMode()))
            return "全部历史数据";
        if ("RECENT".equalsIgnoreCase(intent.getTrainMode()))
            return "最近" + intent.getTrainingPeriods() + "个周期";
        if ("RANGE".equalsIgnoreCase(intent.getTrainMode()))
            return intent.getTrainingStartDate() + "至" + intent.getTrainingEndDate();
        return "模型推荐范围";
    }

    private ExecutionResult unsupportedCustomer(AiIntent intent) {
        StringBuilder answer = new StringBuilder("已识别客户“").append(intent.getCustomerName()).append("”");
        if (TextUtils.hasText(intent.getCustomerCode()))
            answer.append("（编码：").append(intent.getCustomerCode()).append("）");
        answer.append("，所属地区为").append(nullToDash(intent.getRegionName())).append("，行业为").append(nullToDash(intent.getIndustryName())).append("。\n\n")
                .append("该客户目前不在任何已配置的短期预测模型支持范围内，因此无法直接训练或预测。这不是“尚未训练”，而是“没有适用模型”。\n\n").append("你可以：\n1. 在模型配置中把该客户或对应地区、行业加入支持范围；\n").append("2. 问我“短期智能体支持哪些客户”，选择当前可预测的客户。");
        return new ExecutionResult("UNSUPPORTED", answer.toString());
    }

    private ExecutionResult queryForecast(AiConversationTb conversation, AiIntent intent) {
        String batchNo = TextUtils.hasText(intent.getForecastBatchNo()) ? intent.getForecastBatchNo() : conversation.getLastForecastBatchNo();
        if (!TextUtils.hasText(batchNo))
            return new ExecutionResult("NEED_INFORMATION", "请提供预测批次号，或者先在当前会话中发起预测。");
        ModelForecastRecordTb record = forecastRecordMapper.selectOne(Wrappers.<ModelForecastRecordTb>lambdaQuery().eq(ModelForecastRecordTb::getForecastBatchNo, batchNo).last("limit 1"));
        if (record == null)
            throw new BusinessException("预测批次不存在：" + batchNo);
        List<ModelForecastResultTb> points = forecastResultMapper
                .selectList(Wrappers.<ModelForecastResultTb>lambdaQuery().eq(ModelForecastResultTb::getForecastBatchNo, batchNo).orderByAsc(ModelForecastResultTb::getForecastDate));
        String summary = points.isEmpty() ? "尚未产生预测结果" : resultSummary(points);
        return new ExecutionResult("COMPLETED", "预测批次 " + batchNo + "，状态：" + record.getStatus() + "，" + summary + "。");
    }

    private ExecutionResult generalChat(AiConversationTb conversation, AiIntent intent) {
        List<AiConversationMessageTb> history = messages(conversation.getId());
        String currentMessage = history.get(history.size() - 1).getContent();
        ArrayNode array = objectMapper.createArrayNode();
        List<AiConversationMessageTb> previous = history.subList(0, history.size() - 1);
        previous.stream().skip(Math.max(0, previous.size() - 8))
                .forEach(item -> array.addObject().put("role", item.getRole()).put("content", item.getContent()));
        ForecastResult forecastResult = buildStoredForecastResult(conversation);
        // 普通问答中心由 Python Agent 识别 GREETING/GENERAL_CHAT；预测绑定会话才加载预测上下文。
        if ("GENERAL".equals(conversation.getScene())) {
            try {
                List<Map<String, String>> recentHistory = previous.stream().skip(Math.max(0, previous.size() - 12))
                        .filter(item -> TextUtils.hasText(item.getRole()) && TextUtils.hasText(item.getContent()))
                        .map(item -> Map.of("role", item.getRole(), "content", item.getContent())).toList();
                AgentConversationClient.AgentReply reply = agentConversationClient.chat(conversation.getConversationNo(), currentMessage, recentHistory);
                return new ExecutionResult(reply.status(), reply.answer());
            } catch (RuntimeException exception) {
                log.error("Python Agent普通会话失败, conversationNo={}", conversation.getConversationNo(), exception);
                String reason = TextUtils.hasText(exception.getMessage()) ? exception.getMessage() : exception.getClass().getSimpleName();
                return new ExecutionResult("FAILED", "智能助手暂时无法回答，请稍后重试。原因：" + reason);
            }
        }
        JsonNode context = forecastResult == null
                ? null
                : objectMapper.valueToTree(Map.of("instruction", "请只根据当前预测结果和会话内容回答。若问题涉及计划、天气、生产安排等未提供的外部信息，明确说明无法从现有数据确认，不得猜测。", "forecastResult", forecastResult));
        try {
            String answer = llmClient.chat(intent.getAgentCode(), currentMessage, context, array);
            return new ExecutionResult("COMPLETED", answer);
        } catch (RuntimeException exception) {
            log.error("开放式问答失败, conversationNo={}", conversation.getConversationNo(), exception);
            return new ExecutionResult("FAILED", "AI 暂时没有完成回答，请稍后重试。已有训练和预测任务不会受到影响。若要查询任务，可直接输入批次号。");
        }
    }

    private String requiredParameterQuestion(AiIntent intent, AiConversationTb conversation) {
        if ((intent.getIntent() == AiIntentType.START_TRAINING || intent.getIntent() == AiIntentType.START_FORECAST) && "short-term".equals(intent.getAgentCode())
                && !TextUtils.hasText(intent.getCustomerCode()) && !Boolean.TRUE.equals(intent.getBulkScope()))
            return "请告诉我需要处理的客户名称。";
        if (intent.getIntent() == AiIntentType.QUERY_TRAINING && !TextUtils.hasText(intent.getTrainBatchNo()) && !TextUtils.hasText(conversation.getLastTrainBatchNo()))
            return "请提供训练批次号。";
        if ((intent.getIntent() == AiIntentType.QUERY_FORECAST || intent.getIntent() == AiIntentType.QUERY_RESULT) && !TextUtils.hasText(intent.getForecastBatchNo())
                && !TextUtils.hasText(conversation.getLastForecastBatchNo()))
            return "请提供预测批次号。";
        return null;
    }

    private AiChatResponse confirm(AiConversationTb conversation, AiIntent intent, String answer) {
        try {
            conversation.setPendingIntentJson(objectMapper.writeValueAsString(intent));
        } catch (Exception exception) {
            throw new IllegalStateException("保存待确认意图失败", exception);
        }
        conversation.setStatus("WAITING_CONFIRM");
        conversation.setUpdatedAt(new Date());
        conversationMapper.updateById(conversation);
        return respond(conversation, "NEED_CONFIRMATION", answer, intent);
    }

    private AiChatResponse needInformation(AiConversationTb conversation, AiIntent intent, String answer) {
        try {
            conversation.setPendingIntentJson(objectMapper.writeValueAsString(intent));
        } catch (Exception exception) {
            throw new IllegalStateException("保存待补充意图失败", exception);
        }
        conversation.setStatus("WAITING_INPUT");
        conversation.setUpdatedAt(new Date());
        conversationMapper.updateById(conversation);
        return respond(conversation, "NEED_INFORMATION", answer, intent);
    }

    private AiChatResponse respond(AiConversationTb conversation, String status, String answer, AiIntent intent) {
        return respond(conversation, status, answer, intent, null);
    }

    private AiChatResponse respond(AiConversationTb conversation, String status, String answer, AiIntent intent, BulkExecution bulkExecution) {
        ExecutionProgress progress = bulkExecution == null ? buildProgress(status, intent, conversation) : buildBulkProgress(bulkExecution);
        ForecastResult forecastResult = buildForecastResult(status, intent, conversation);
        ForecastInsight insight = forecastResult == null ? ForecastInsight.empty() : generateForecastInsight(intent, forecastResult);
        saveMessage(conversation.getId(), "assistant", answer, intent.getIntent().name(), new StoredMessagePayload(progress, forecastResult, insight.analysis(), insight.suggestedQuestions(), bulkExecution, null));
        boolean bulk = Boolean.TRUE.equals(intent.getBulkScope());
        return new AiChatResponse(conversation.getConversationNo(), status, answer, intent, intent.getCandidates(), bulk ? null : conversation.getLastTrainBatchNo(),
                bulk ? null : conversation.getLastForecastBatchNo(), progress, forecastResult, insight.analysis(), insight.suggestedQuestions(), null);
    }

    /** 自动训练完成后的预测属于原用户请求，更新原流程消息，不额外创建助手消息。 */
    private AiChatResponse respondByUpdatingWorkflow(AiConversationTb conversation, String status, String answer, AiIntent intent, String trainBatchNo) {
        ExecutionProgress progress = buildProgress(status, intent, conversation);
        if ("FAILED".equals(status) && progress != null)
            progress = withForecastFailure(progress, answer);
        ForecastResult forecastResult = buildForecastResult(status, intent, conversation);
        ForecastInsight insight = forecastResult == null ? ForecastInsight.empty() : generateForecastInsight(intent, forecastResult);
        StoredMessagePayload payload = new StoredMessagePayload(progress, forecastResult, insight.analysis(), insight.suggestedQuestions(), null, null);
        List<AiConversationMessageTb> candidates = messageMapper.selectList(Wrappers.<AiConversationMessageTb>lambdaQuery().eq(AiConversationMessageTb::getConversationId, conversation.getId())
                .eq(AiConversationMessageTb::getRole, "assistant").orderByDesc(AiConversationMessageTb::getId).last("limit 20"));
        AiConversationMessageTb target = candidates.stream().filter(message -> {
            ExecutionProgress stored = readStructuredPayload(message.getStructuredJson()).progress();
            return stored != null && stored.steps().stream().anyMatch(step -> trainBatchNo.equals(step.detail()));
        }).findFirst().orElse(null);
        if (target != null) {
            if (!TextUtils.hasText(target.getContent()))
                target.setContent(answer);
            else if (!target.getContent().contains(answer))
                target.setContent(target.getContent() + "\n\n" + answer);
            target.setIntentCode(AiIntentType.START_FORECAST.name());
            try {
                target.setStructuredJson(objectMapper.writeValueAsString(payload));
            } catch (Exception exception) {
                throw new IllegalStateException("保存会话执行结果失败", exception);
            }
            messageMapper.updateById(target);
        } else {
            saveMessage(conversation.getId(), "assistant", answer, AiIntentType.START_FORECAST.name(), payload);
        }
        return new AiChatResponse(conversation.getConversationNo(), status, answer, intent, intent.getCandidates(), conversation.getLastTrainBatchNo(), conversation.getLastForecastBatchNo(), progress,
                forecastResult, insight.analysis(), insight.suggestedQuestions(), null);
    }

    private ExecutionProgress withForecastFailure(ExecutionProgress progress, String reason) {
        List<Step> steps = progress.steps().stream().map(step -> step.index() == 3 ? new Step(step.index(), step.name(), "FAILED", reason) : step).toList();
        return new ExecutionProgress(progress.title(), 3, progress.totalSteps(), steps);
    }

    private ForecastInsight generateForecastInsight(AiIntent intent, ForecastResult result) {
        List<String> fallbackQuestions = List.of("哪些日期变化最明显？", "这个趋势可能受哪些已知因素影响？");
        try {
            var generated = forecastAnalysisService.get(result.batchNo());
            if (generated != null && "SUCCESS".equals(generated.analysisStatus()) && TextUtils.hasText(generated.analysisContent()))
                return new ForecastInsight(generated.analysisContent(), fallbackQuestions);
            // Python Agent仍在异步生成时不再由Java重复调用模型，页面稍后从同一分析记录刷新。
            return new ForecastInsight(null, fallbackQuestions);
        } catch (Exception exception) {
            return new ForecastInsight(null, fallbackQuestions);
        }
    }

    private String fallbackForecastAnalysis(ForecastResult result) {
        BigDecimal first = result.points().get(0).value();
        BigDecimal last = result.points().get(result.points().size() - 1).value();
        int comparison = last.compareTo(first);
        String trend = comparison > 0 ? "上升" : comparison < 0 ? "下降" : "基本平稳";
        return "预测期整体呈" + trend + "趋势，期初值为 " + first + "，期末值为 " + last + "。" + "当前仅依据预测序列判断走势，缺少外部影响数据，暂不能进一步确定原因。";
    }

    private String stripJsonFence(String value) {
        String text = value == null ? "" : value.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```(?:json)?\\s*", "");
            text = text.replaceFirst("\\s*```$", "");
        }
        return text.trim();
    }

    private ForecastResult buildForecastResult(String status, AiIntent intent, AiConversationTb conversation) {
        if (Boolean.TRUE.equals(intent.getBulkScope()))
            return null;
        if (!"COMPLETED".equals(status) || !TextUtils.hasText(conversation.getLastForecastBatchNo())
                || (intent.getIntent() != AiIntentType.START_FORECAST && intent.getIntent() != AiIntentType.QUERY_FORECAST && intent.getIntent() != AiIntentType.QUERY_RESULT))
            return null;
        List<ModelForecastResultTb> rows = forecastResultMapper.selectList(
                Wrappers.<ModelForecastResultTb>lambdaQuery().eq(ModelForecastResultTb::getForecastBatchNo, conversation.getLastForecastBatchNo()).orderByAsc(ModelForecastResultTb::getForecastDate));
        List<ForecastPoint> points = rows.stream().filter(row -> row.getForecastValue() != null).map(row -> new ForecastPoint(row.getForecastDate(), row.getForecastValue())).toList();
        if (points.isEmpty())
            return null;
        BigDecimal total = points.stream().map(ForecastPoint::value).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal average = total.divide(BigDecimal.valueOf(points.size()), 2, java.math.RoundingMode.HALF_UP);
        BigDecimal minimum = points.stream().map(ForecastPoint::value).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal maximum = points.stream().map(ForecastPoint::value).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        ModelForecastRecordTb forecastRecord = forecastRecordMapper.selectOne(Wrappers.<ModelForecastRecordTb>lambdaQuery()
                .select(ModelForecastRecordTb::getCustomerName, ModelForecastRecordTb::getRegionCode, ModelForecastRecordTb::getIndustryCode, ModelForecastRecordTb::getCustomerCode)
                .eq(ModelForecastRecordTb::getForecastBatchNo, conversation.getLastForecastBatchNo()).last("limit 1"));
        ModelTrainRecordTb trainRecord = TextUtils.hasText(conversation.getLastTrainBatchNo())
                ? trainRecordMapper.selectOne(Wrappers.<ModelTrainRecordTb>lambdaQuery().eq(ModelTrainRecordTb::getBatchNo, conversation.getLastTrainBatchNo()).last("limit 1"))
                : null;
        TrainingResult training = trainRecord == null
                ? null
                : new TrainingResult(trainRecord.getBatchNo(), trainRecord.getTrainStartDate(), trainRecord.getTrainEndDate(), trainRecord.getBestModel(), trainRecord.getMape(),
                        trainRecord.getWmape(), trainRecord.getSmape(), trainRecord.getMae(), trainRecord.getRmse(), trainRecord.getR2());
        String firstForecastDate = points.get(0).date();
        List<DataDailySalesTb> historyRows = new java.util.ArrayList<>(
                dailySalesMapper.selectList(Wrappers.<DataDailySalesTb>lambdaQuery().eq(DataDailySalesTb::getCustomerCode, intent.getCustomerCode())
                        .lt(DataDailySalesTb::getStatDate, firstForecastDate).isNotNull(DataDailySalesTb::getGasSales).orderByDesc(DataDailySalesTb::getStatDate).last("limit 30")));
        java.util.Collections.reverse(historyRows);
        List<ForecastPoint> historyPoints = historyRows.stream().map(row -> new ForecastPoint(row.getStatDate(), new BigDecimal(row.getGasSales()))).toList();
        Map<String, BigDecimal> lastYearActuals = loadLastYearActuals(forecastRecord, points.stream().map(ForecastPoint::date).toList());
        List<ForecastPoint> lastYearPoints = points.stream().map(point -> {
            return new ForecastPoint(point.date(), lastYearActuals.get(point.date()));
        }).filter(point -> point.value() != null).toList();
        List<BacktestPoint> backtestPoints = TextUtils.hasText(conversation.getLastTrainBatchNo())
                ? trainBacktestMapper
                        .selectList(Wrappers.<ModelTrainBacktestTb>lambdaQuery().eq(ModelTrainBacktestTb::getTrainBatchNo, conversation.getLastTrainBatchNo())
                                .orderByAsc(ModelTrainBacktestTb::getTrainDate))
                        .stream().map(row -> new BacktestPoint(formatDate(row.getTrainDate()), row.getActualValue(), row.getPredictedValue())).toList()
                : List.of();
        String customerName = forecastRecord != null && TextUtils.hasText(forecastRecord.getCustomerName()) ? forecastRecord.getCustomerName() : intent.getCustomerName();
        return new ForecastResult(conversation.getLastForecastBatchNo(), customerName, average, minimum, maximum, training, points, historyPoints, lastYearPoints, backtestPoints);
    }

    private Map<String, BigDecimal> loadLastYearActuals(ModelForecastRecordTb record, List<String> forecastDates) {
        Map<String, String> sourceToDisplay = new LinkedHashMap<>();
        for (String date : forecastDates) {
            if (!TextUtils.hasText(date))
                continue;
            sourceToDisplay.putIfAbsent(LocalDate.parse(date).minusYears(1).toString(), date);
        }
        if (sourceToDisplay.isEmpty())
            return Map.of();
        QueryWrapper<DataDailySalesTb> query = Wrappers.<DataDailySalesTb>query().select("stat_date", "SUM(CAST(gas_sales AS DECIMAL(18,4))) AS gas_sales").in("stat_date", sourceToDisplay.keySet())
                .isNotNull("gas_sales");
        if (record != null) {
            if (TextUtils.hasText(record.getRegionCode()))
                query.eq("region_code", record.getRegionCode());
            if (TextUtils.hasText(record.getIndustryCode()))
                query.eq("industry_code", record.getIndustryCode());
            if (TextUtils.hasText(record.getCustomerCode()))
                query.eq("customer_code", record.getCustomerCode());
        }
        query.groupBy("stat_date");
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (Map<String, Object> row : dailySalesMapper.selectMaps(query)) {
            String sourceDate = stringMapValue(row, "stat_date");
            Object value = objectMapValue(row, "gas_sales");
            if (value != null && sourceToDisplay.containsKey(sourceDate))
                result.put(sourceToDisplay.get(sourceDate), new BigDecimal(value.toString()));
        }
        return result;
    }

    private Object objectMapValue(Map<String, Object> row, String key) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (key.equalsIgnoreCase(entry.getKey()))
                return entry.getValue();
        }
        return null;
    }

    private String stringMapValue(Map<String, Object> row, String key) {
        Object value = objectMapValue(row, key);
        return value == null ? "-" : String.valueOf(value);
    }

    private String formatDate(Date value) {
        return value == null ? null : new java.text.SimpleDateFormat("yyyy-MM-dd").format(value);
    }

    private ExecutionProgress buildProgress(String status, AiIntent intent, AiConversationTb conversation) {
        if ("NEED_INFORMATION".equals(status) || "NEED_CONFIRMATION".equals(status) || "UNSUPPORTED".equals(status))
            return null;
        if (intent.getIntent() != AiIntentType.START_TRAINING && intent.getIntent() != AiIntentType.START_FORECAST && intent.getIntent() != AiIntentType.QUERY_TRAINING
                && intent.getIntent() != AiIntentType.QUERY_FORECAST && intent.getIntent() != AiIntentType.QUERY_RESULT)
            return null;
        if (Boolean.TRUE.equals(intent.getBulkScope())) {
            String scope = TextUtils.hasText(intent.getRegionName()) ? intent.getRegionName() : "";
            if (TextUtils.hasText(intent.getIndustryName()))
                scope += (TextUtils.hasText(scope) ? " / " : "") + intent.getIndustryName() + "行业";
            scope = (TextUtils.hasText(scope) ? scope : displayScope(intent)) + "全部客户";
            if ("FAILED".equals(status)) {
                return new ExecutionProgress("批量智能预测", 2, 3,
                        List.of(new Step(1, "识别批量范围", "SUCCESS", scope), new Step(2, "逐客户创建任务", "FAILED", "任务创建失败"),
                                new Step(3, "后台执行训练与预测", "WAITING", "未开始执行")));
            }
            if ("COMPLETED".equals(status)) {
                return new ExecutionProgress("批量智能预测", 3, 3,
                        List.of(new Step(1, "识别批量范围", "SUCCESS", scope), new Step(2, "逐客户创建任务", "SUCCESS", "已完成任务分发"),
                                new Step(3, "后台执行训练与预测", "SUCCESS", "已完成")));
            }
            return new ExecutionProgress("批量智能预测", 3, 3,
                    List.of(new Step(1, "识别批量范围", "SUCCESS", scope), new Step(2, "逐客户创建任务", "SUCCESS", "已完成任务分发"), new Step(3, "后台执行训练与预测", "RUNNING", "多个批次并行或排队执行")));
        }
        if (intent.getIntent() == AiIntentType.START_TRAINING || intent.getIntent() == AiIntentType.QUERY_TRAINING) {
            int current = "COMPLETED".equals(status) ? 3 : 2;
            Step trainStep = "FAILED".equals(status) ? new Step(2, "准备数据并执行训练", "FAILED", conversation.getLastTrainBatchNo()) : step(2, "准备数据并执行训练", current, conversation.getLastTrainBatchNo());
            return new ExecutionProgress("模型训练", current, 3,
                    List.of(step(1, "识别训练对象", current, displayScope(intent)), trainStep, step(3, "保存训练结果", current, "COMPLETED".equals(status) ? "已完成" : null)));
        }
        int current;
        if ("COMPLETED".equals(status) && TextUtils.hasText(conversation.getLastForecastBatchNo()))
            current = 4;
        else if ("WAITING_TRAINING".equals(status))
            current = 2;
        else
            current = 3;
        Step predictStep = "FAILED".equals(status) ? new Step(3, "执行预测", "FAILED", "预测执行失败") : step(3, "执行预测", current, conversation.getLastForecastBatchNo());
        return new ExecutionProgress("智能预测", current, 4, List.of(step(1, "识别客户与预测范围", current, displayScope(intent)), step(2, "检查并训练模型", current, conversation.getLastTrainBatchNo()), predictStep,
                step(4, "保存并展示结果", current, current == 4 ? "已生成预测结果" : null)));
    }

    private Step step(int index, String name, int current, String detail) {
        String stepStatus = index < current ? "SUCCESS" : index == current ? "RUNNING" : "WAITING";
        if (index == current && ("已完成".equals(detail) || "已生成预测结果".equals(detail)))
            stepStatus = "SUCCESS";
        return new Step(index, name, stepStatus, detail);
    }

    private AiConversationTb requireConversation(AiChatRequest request) {
        if (TextUtils.hasText(request.getForecastBatchNo()))
            return requireForecastConversation(request.getForecastBatchNo(), request.getConversationNo());
        if (TextUtils.hasText(request.getConversationNo()))
            return findOwned(request.getConversationNo());
        return createConversation(request.getMessage());
    }

    /**
     * forecastBatchNo是预测页面会话的业务主键。即使前端丢失conversationNo，刷新后也能找回同一会话；
     * 若同时传入conversationNo，则校验它确实属于当前预测，避免不同预测之间串话。
     */
    private AiConversationTb requireForecastConversation(String forecastBatchNo, String conversationNo) {
        if (!TextUtils.hasText(forecastBatchNo)) throw new BusinessException("预测批次号不能为空");
        String userId = String.valueOf(SecurityContextHolder.getUserId());
        if (TextUtils.hasText(conversationNo)) {
            AiConversationTb supplied = findOwned(conversationNo);
            if (!"FORECAST_ANALYSIS".equals(supplied.getScene()) || !forecastBatchNo.equals(supplied.getBusinessNo()))
                throw new BusinessException("当前会话不属于该预测批次，请刷新预测页面后重试");
            return supplied;
        }
        AiConversationTb existing = conversationMapper.selectOne(Wrappers.<AiConversationTb>lambdaQuery()
                .eq(AiConversationTb::getCreatedBy, userId).eq(AiConversationTb::getScene, "FORECAST_ANALYSIS")
                .eq(AiConversationTb::getBusinessType, "FORECAST").eq(AiConversationTb::getBusinessNo, forecastBatchNo).last("limit 1"));
        if (existing != null) {
            syncStoredAutomaticAnalysis(existing);
            return existing;
        }
        ModelForecastRecordTb forecast = forecastRecordMapper.selectOne(Wrappers.<ModelForecastRecordTb>lambdaQuery()
                .eq(ModelForecastRecordTb::getForecastBatchNo, forecastBatchNo).last("limit 1"));
        if (forecast == null) throw new BusinessException("预测批次不存在：" + forecastBatchNo);
        AiConversationTb created;
        try {
            created = createForecastConversation(forecast, userId);
        } catch (DuplicateKeyException exception) {
            // 同一用户同时打开同一预测时由唯一键防重，复用另一请求已经创建的会话。
            created = conversationMapper.selectOne(Wrappers.<AiConversationTb>lambdaQuery()
                    .eq(AiConversationTb::getCreatedBy, userId).eq(AiConversationTb::getScene, "FORECAST_ANALYSIS")
                    .eq(AiConversationTb::getBusinessType, "FORECAST").eq(AiConversationTb::getBusinessNo, forecastBatchNo).last("limit 1"));
        }
        syncStoredAutomaticAnalysis(created);
        return created;
    }

    /** 创建当前用户与预测批次唯一绑定的会话；公共预测解析不在此表中保存主副本。 */
    private AiConversationTb createForecastConversation(ModelForecastRecordTb forecast, String userId) {
        Date now = new Date();
        AiConversationTb value = new AiConversationTb();
        value.setConversationNo("AICHAT-" + UUID.randomUUID().toString().replace("-", ""));
        value.setTitle((TextUtils.hasText(forecast.getCustomerName()) ? forecast.getCustomerName() : "预测结果") + " AI分析");
        value.setAgentCode(TextUtils.hasText(forecast.getAgentCode()) ? forecast.getAgentCode() : "short-term");
        value.setScene("FORECAST_ANALYSIS");
        value.setBusinessType("FORECAST");
        value.setBusinessNo(forecast.getForecastBatchNo());
        value.setLastForecastBatchNo(forecast.getForecastBatchNo());
        value.setStatus("ACTIVE");
        value.setCreatedBy(userId);
        value.setCreatedAt(now);
        value.setUpdatedAt(now);
        conversationMapper.insert(value);
        return value;
    }

    /** 将历史预测已经生成但尚未归档到会话的自动分析补入一次，兼容迁移前的数据。 */
    private void syncStoredAutomaticAnalysis(AiConversationTb conversation) {
        long exists = messageMapper.selectCount(Wrappers.<AiConversationMessageTb>lambdaQuery()
                .eq(AiConversationMessageTb::getConversationId, conversation.getId()).eq(AiConversationMessageTb::getIntentCode, "FORECAST_AUTO_ANALYSIS"));
        if (exists > 0) return;
        var analysis = forecastAnalysisService.get(conversation.getBusinessNo());
        if (analysis == null || !"SUCCESS".equals(analysis.analysisStatus()) || !TextUtils.hasText(analysis.analysisContent())) return;
        saveMessage(conversation.getId(), "assistant", analysis.analysisContent(), "FORECAST_AUTO_ANALYSIS", null);
    }

    /** 创建只绑定用户的普通问答会话；一个用户可以拥有多个。 */
    private AiConversationTb createConversation(String firstMessage) {
        Date now = new Date();
        AiConversationTb value = new AiConversationTb();
        value.setConversationNo("AICHAT-" + UUID.randomUUID().toString().replace("-", ""));
        String title = TextUtils.hasText(firstMessage) ? firstMessage : "新会话";
        value.setTitle(title.length() > 30 ? title.substring(0, 30) : title);
        value.setAgentCode(null);
        value.setScene("GENERAL");
        value.setBusinessType(null);
        value.setBusinessNo(null);
        value.setLastForecastBatchNo(null);
        value.setStatus("ACTIVE");
        value.setCreatedBy(String.valueOf(SecurityContextHolder.getUserId()));
        value.setCreatedAt(now);
        value.setUpdatedAt(now);
        conversationMapper.insert(value);
        return value;
    }

    private AiConversationTb findOwned(String conversationNo) {
        AiConversationTb value = conversationMapper.selectOne(Wrappers.<AiConversationTb>lambdaQuery().eq(AiConversationTb::getConversationNo, conversationNo)
                .eq(AiConversationTb::getCreatedBy, String.valueOf(SecurityContextHolder.getUserId())).last("limit 1"));
        if (value == null)
            throw new BusinessException("会话不存在或无权访问");
        return value;
    }

    private void saveMessage(Long conversationId, String role, String content, String intentCode, Object structured) {
        AiConversationMessageTb value = new AiConversationMessageTb();
        value.setConversationId(conversationId);
        value.setRole(role);
        value.setContent(content);
        value.setIntentCode(intentCode);
        try {
            value.setStructuredJson(structured == null ? null : objectMapper.writeValueAsString(structured));
        } catch (Exception ignored) {
            value.setStructuredJson(null);
        }
        value.setCreatedAt(new Date());
        messageMapper.insert(value);
    }

    private List<AiConversationMessageTb> messages(Long conversationId) {
        return messageMapper.selectList(Wrappers.<AiConversationMessageTb>lambdaQuery().eq(AiConversationMessageTb::getConversationId, conversationId).orderByAsc(AiConversationMessageTb::getId));
    }

    private AiConversationResponse toResponse(AiConversationTb item, boolean includeMessages) {
        if (!includeMessages)
            return new AiConversationResponse(item.getConversationNo(), item.getTitle(), item.getStatus(), item.getScene(), item.getBusinessType(), item.getBusinessNo(),
                    item.getLastTrainBatchNo(), item.getLastForecastBatchNo(), item.getUpdatedAt(), List.of());
        List<AiConversationMessageTb> storedMessages = messages(item.getId());
        Long fallbackMessageId = storedMessages.stream().filter(message -> "assistant".equals(message.getRole()) && isForecastIntent(message.getIntentCode())).map(AiConversationMessageTb::getId)
                .max(Long::compareTo).orElse(null);
        ForecastResult fallbackResult = buildStoredForecastResult(item);
        List<AiConversationResponse.Message> messages = storedMessages.stream().map(message -> {
            StoredMessagePayload payload = readStructuredPayload(message.getStructuredJson());
            ExecutionProgress progress = payload.bulkExecution() == null ? normalizeStoredProgress(payload.progress(), message.getContent()) : buildBulkProgress(payload.bulkExecution());
            String content = payload.bulkExecution() == null ? message.getContent() : withBulkSummary(message.getContent(), payload.bulkExecution());
            ForecastResult forecastResult = payload.forecastResult();
            // 历史会话保存的旧结构没有训练摘要；打开会话时用数据库中的最新完整结构补齐。
            // 仅对“预测已经完成”的旧消息补齐结构。正在训练/等待预测的消息绝不能挂载会话上一次结果。
            if ((forecastResult == null || forecastResult.training() == null) && message.getId().equals(fallbackMessageId) && isCompletedForecastProgress(progress)) {
                forecastResult = fallbackResult;
            }
            String analysis = payload.analysis();
            if (!TextUtils.hasText(analysis) && forecastResult != null) {
                try {
                    var automatic = forecastAnalysisService.get(forecastResult.batchNo());
                    if (automatic != null && "SUCCESS".equals(automatic.analysisStatus())) analysis = automatic.analysisContent();
                } catch (RuntimeException ignored) {
                    // 自动分析仍在生成或查询暂时失败时先返回预测结果，下次加载会话再补齐。
                }
            }
            return new AiConversationResponse.Message(message.getId(), message.getRole(), content, message.getCreatedAt(), progress, forecastResult, analysis,
                    payload.suggestedQuestions(), payload.presentation());
        }).toList();
        return new AiConversationResponse(item.getConversationNo(), item.getTitle(), item.getStatus(), item.getScene(), item.getBusinessType(), item.getBusinessNo(),
                item.getLastTrainBatchNo(), item.getLastForecastBatchNo(), item.getUpdatedAt(), messages);
    }

    /** 修正旧会话中已失败但被固定保存为 RUNNING 的批量进度。 */
    private ExecutionProgress normalizeStoredProgress(ExecutionProgress progress, String content) {
        if (progress == null || !"批量智能预测".equals(progress.title()) || !TextUtils.hasText(content)
                || !isActualExecutionFailure(content))
            return progress;
        boolean stillRunning = progress.steps().stream().anyMatch(step -> "RUNNING".equals(step.status()));
        if (!stillRunning)
            return progress;
        String scope = progress.steps().isEmpty() ? "已识别批量范围" : progress.steps().get(0).detail();
        return new ExecutionProgress(progress.title(), 2, progress.totalSteps(),
                List.of(new Step(1, "识别批量范围", "SUCCESS", scope), new Step(2, "逐客户创建任务", "FAILED", "任务创建失败"),
                        new Step(3, "后台执行训练与预测", "WAITING", "未开始执行")));
    }

    private boolean isActualExecutionFailure(String content) {
        return content.contains("执行到业务校验阶段失败") || content.contains("本次操作未成功") || content.contains("本次处理没有完成")
                || content.matches("(?s).*(?:创建失败|失败)\\s*[1-9]\\d*\\s*个.*");
    }

    private ExecutionProgress buildBulkProgress(BulkExecution bulk) {
        BulkSnapshot snapshot = bulkSnapshot(bulk);
        String scopeDetail = "共 " + snapshot.total() + " 个客户";
        String current = snapshot.currentCustomer() == null ? "" : "，当前：" + snapshot.currentCustomer() + "（" + snapshot.currentAction() + "）";
        String detail = "已完成 " + snapshot.completed() + "/" + snapshot.total() + current + "；预测成功 " + snapshot.succeeded() + "，失败 " + snapshot.failed() + "，执行中 "
                + snapshot.running() + "，等待 " + snapshot.waiting();
        boolean finished = snapshot.completed() >= snapshot.total() && snapshot.total() > 0;
        String status;
        if (!finished) {
            status = "RUNNING";
        } else if (snapshot.failed() == 0) {
            status = "SUCCESS";
        } else if (snapshot.failed() >= snapshot.total()) {
            status = "FAILED";
        } else {
            status = "PARTIAL_FAILED";
        }
        return new ExecutionProgress("批量智能预测", 3, 3,
                List.of(new Step(1, "识别批量范围", "SUCCESS", scopeDetail), new Step(2, "逐客户创建任务", "SUCCESS", "已完成任务分发"),
                        new Step(3, "后台执行训练与预测", status, detail)));
    }

    private String withBulkSummary(String original, BulkExecution bulk) {
        BulkSnapshot snapshot = bulkSnapshot(bulk);
        String marker = "\n\n批量实时进度：";
        int markerIndex = original == null ? -1 : original.indexOf(marker);
        String base = markerIndex >= 0 ? original.substring(0, markerIndex) : original == null ? "" : original;
        String current = snapshot.currentCustomer() == null ? "" : "；当前客户：" + snapshot.currentCustomer() + "（" + snapshot.currentAction() + "）";
        StringBuilder summary = new StringBuilder(marker).append("已完成 ").append(snapshot.completed()).append("/").append(snapshot.total()).append("；预测成功 ")
                .append(snapshot.succeeded()).append("；失败 ").append(snapshot.failed()).append("；执行中 ").append(snapshot.running()).append("；等待 ").append(snapshot.waiting()).append(current).append("。");
        if (!snapshot.failures().isEmpty())
            summary.append("\n失败客户：\n- ").append(String.join("\n- ", snapshot.failures()));
        return base + summary;
    }

    private BulkSnapshot bulkSnapshot(BulkExecution bulk) {
        if (bulk == null || bulk.tasks() == null || bulk.tasks().isEmpty())
            return new BulkSnapshot(0, 0, 0, 0, 0, 0, null, null, List.of());
        List<String> trainBatchNos = bulk.tasks().stream().map(BulkTask::trainBatchNo).filter(TextUtils::hasText).toList();
        Map<String, ModelTrainRecordTb> trains = new LinkedHashMap<>();
        if (!trainBatchNos.isEmpty())
            trainRecordMapper.selectList(Wrappers.<ModelTrainRecordTb>lambdaQuery().in(ModelTrainRecordTb::getBatchNo, trainBatchNos)).forEach(item -> trains.put(item.getBatchNo(), item));
        List<String> customerCodes = bulk.tasks().stream().map(BulkTask::customerCode).filter(TextUtils::hasText).distinct().toList();
        Map<String, ModelForecastRecordTb> forecasts = new LinkedHashMap<>();
        if (!customerCodes.isEmpty()) {
            var query = Wrappers.<ModelForecastRecordTb>lambdaQuery().in(ModelForecastRecordTb::getCustomerCode, customerCodes).orderByDesc(ModelForecastRecordTb::getId);
            if (bulk.startedAt() != null)
                query.ge(ModelForecastRecordTb::getCreatedAt, bulk.startedAt());
            forecastRecordMapper.selectList(query).forEach(item -> forecasts.putIfAbsent(item.getCustomerCode(), item));
        }
        int success = 0, failed = 0, running = 0, waiting = 0;
        String currentCustomer = null, currentAction = null;
        List<String> failures = new java.util.ArrayList<>();
        for (BulkTask task : bulk.tasks()) {
            ModelTrainRecordTb train = TextUtils.hasText(task.trainBatchNo()) ? trains.get(task.trainBatchNo()) : null;
            ModelForecastRecordTb forecast = forecasts.get(task.customerCode());
            if (forecast != null && Integer.valueOf(2).equals(forecast.getStatus())) {
                success++;
            } else if (forecast != null && Integer.valueOf(3).equals(forecast.getStatus())) {
                failed++;
                if (failures.size() < 20)
                    failures.add(task.customerName() + "：预测失败");
            } else if (forecast != null && Integer.valueOf(1).equals(forecast.getStatus())) {
                running++;
                if (currentCustomer == null) { currentCustomer = task.customerName(); currentAction = "预测中"; }
            } else if (train != null && ("FAILED".equals(train.getStatus()) || "FAILED_FINAL".equals(train.getStatus()) || "PREPARE_FAILED".equals(train.getStatus()))) {
                failed++;
                if (failures.size() < 20)
                    failures.add(task.customerName() + "：" + (TextUtils.hasText(train.getErrorMessage()) ? train.getErrorMessage() : "训练失败"));
            } else if (train != null && "SUCCESS".equals(train.getStatus())) {
                waiting++;
                if (currentCustomer == null) { currentCustomer = task.customerName(); currentAction = "等待预测"; }
            } else if (train != null && ("RUNNING".equals(train.getStatus()) || "PREPARING".equals(train.getStatus()) || "CLAIMED".equals(train.getStatus()))) {
                running++;
                if (currentCustomer == null) { currentCustomer = task.customerName(); currentAction = "训练中"; }
            } else {
                waiting++;
                if (currentCustomer == null) { currentCustomer = task.customerName(); currentAction = "等待训练"; }
            }
        }
        return new BulkSnapshot(bulk.tasks().size(), success + failed, success, failed, running, waiting, currentCustomer, currentAction, failures);
    }

    private ForecastResult buildStoredForecastResult(AiConversationTb conversation) {
        if (!TextUtils.hasText(conversation.getLastForecastBatchNo()))
            return null;
        ModelForecastRecordTb record = forecastRecordMapper
                .selectOne(Wrappers.<ModelForecastRecordTb>lambdaQuery().eq(ModelForecastRecordTb::getForecastBatchNo, conversation.getLastForecastBatchNo()).last("limit 1"));
        if (record == null || record.getStatus() == null || record.getStatus() != 2)
            return null;
        if (TextUtils.hasText(conversation.getLastTrainBatchNo())) {
            ModelTrainRecordTb latestTrain = trainRecordMapper.selectOne(
                    Wrappers.<ModelTrainRecordTb>lambdaQuery().select(ModelTrainRecordTb::getCustomerCode).eq(ModelTrainRecordTb::getBatchNo, conversation.getLastTrainBatchNo()).last("limit 1"));
            if (latestTrain != null && !java.util.Objects.equals(latestTrain.getCustomerCode(), record.getCustomerCode()))
                return null;
        }
        AiIntent intent = new AiIntent();
        intent.setIntent(AiIntentType.QUERY_RESULT);
        intent.setCustomerCode(record.getCustomerCode());
        return buildForecastResult("COMPLETED", intent, conversation);
    }

    private boolean isForecastIntent(String intentCode) {
        return AiIntentType.START_FORECAST.name().equals(intentCode) || AiIntentType.QUERY_FORECAST.name().equals(intentCode) || AiIntentType.QUERY_RESULT.name().equals(intentCode);
    }

    private boolean isCompletedForecastProgress(ExecutionProgress progress) {
        if (progress == null || progress.totalSteps() != 4 || progress.currentStep() != 4)
            return false;
        return progress.steps().stream().anyMatch(step -> step.index() == 4 && "SUCCESS".equals(step.status()));
    }

    private StoredMessagePayload readStructuredPayload(String json) {
        if (!TextUtils.hasText(json))
            return new StoredMessagePayload(null, null, null, List.of(), null, null);
        try {
            var node = objectMapper.readTree(json);
            if (node.has("progress") || node.has("forecastResult") || node.has("presentation"))
                return objectMapper.treeToValue(node, StoredMessagePayload.class);
            // 兼容改造前直接保存 ExecutionProgress 的历史消息。
            return new StoredMessagePayload(objectMapper.treeToValue(node, ExecutionProgress.class), null, null, List.of(), null, null);
        } catch (Exception exception) {
            return new StoredMessagePayload(null, null, null, List.of(), null, null);
        }
    }

    private record StoredMessagePayload(ExecutionProgress progress, ForecastResult forecastResult, String analysis, List<String> suggestedQuestions,
            BulkExecution bulkExecution, JsonNode presentation) {
        private StoredMessagePayload {
            suggestedQuestions = suggestedQuestions == null ? List.of() : suggestedQuestions;
        }
    }

    private record ForecastInsight(String analysis, List<String> suggestedQuestions) {
        private static ForecastInsight empty() {
            return new ForecastInsight(null, List.of());
        }
    }

    private String nextFeatureDate(ModelTrainConfigTb config) {
        DataDailySalesTb latest = dailySalesMapper
                .selectOne(Wrappers.<DataDailySalesTb>lambdaQuery().eq(DataDailySalesTb::getCustomerCode, config.getCustomerCode()).orderByDesc(DataDailySalesTb::getStatDate).last("limit 1"));
        if (latest == null || !TextUtils.hasText(latest.getStatDate()))
            return LocalDate.now().toString();
        return LocalDate.parse(latest.getStatDate()).plusDays(1).toString();
    }

    private String normalizeForecastStartDate(String requestedStartDate, String minimumStartDate) {
        if (!TextUtils.hasText(requestedStartDate))
            return minimumStartDate;
        try {
            LocalDate requested = LocalDate.parse(requestedStartDate);
            LocalDate minimum = LocalDate.parse(minimumStartDate);
            return requested.isBefore(minimum) ? minimumStartDate : requestedStartDate;
        } catch (Exception ignored) {
            return minimumStartDate;
        }
    }

    private String resultSummary(List<ModelForecastResultTb> points) {
        List<BigDecimal> values = points.stream().map(ModelForecastResultTb::getForecastValue).filter(java.util.Objects::nonNull).toList();
        if (values.isEmpty())
            return "共" + points.size() + "条结果，但没有有效预测值";
        BigDecimal min = values.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal max = values.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        return "共" + points.size() + "条结果，范围" + min.stripTrailingZeros().toPlainString() + "至" + max.stripTrailingZeros().toPlainString();
    }

    private String displayScope(AiIntent intent) {
        if (TextUtils.hasText(intent.getCustomerName()))
            return "客户“" + intent.getCustomerName() + "”";
        if (TextUtils.hasText(intent.getRegionName()))
            return "地区“" + intent.getRegionName() + "”";
        return "所选范围";
    }

    private String compactJson(Object value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            return json.length() > 1500 ? json.substring(0, 1500) + "..." : json;
        } catch (Exception exception) {
            return String.valueOf(value);
        }
    }

    private String withCandidates(String message, AiIntent intent) {
        StringBuilder text = new StringBuilder(message);
        for (int index = 0; index < intent.getCandidates().size(); index++) {
            AiIntent.EntityCandidate item = intent.getCandidates().get(index);
            text.append("\n").append(index + 1).append(". ").append(item.getName());
            if (TextUtils.hasText(item.getDescription()))
                text.append("（").append(item.getDescription()).append("）");
        }
        return text.toString();
    }

    private boolean isCancel(String message) {
        return "取消".equals(message.trim()) || "算了".equals(message.trim());
    }

    private record BulkTask(String customerCode, String customerName, String trainBatchNo, String forecastBatchNo) {
    }

    private record BulkExecution(Date startedAt, List<BulkTask> tasks) {
    }

    private record BulkSnapshot(int total, int completed, int succeeded, int failed, int running, int waiting, String currentCustomer, String currentAction, List<String> failures) {
    }

    private record ExecutionResult(String status, String answer, BulkExecution bulkExecution) {
        private ExecutionResult(String status, String answer) {
            this(status, answer, null);
        }
    }
}
