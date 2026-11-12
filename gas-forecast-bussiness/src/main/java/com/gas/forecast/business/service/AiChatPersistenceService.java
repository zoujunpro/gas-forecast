package com.gas.forecast.business.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gas.forecast.business.dto.AiChatRequest;
import com.gas.forecast.business.dto.AiChatResponse;
import com.gas.forecast.business.dto.AiChatContentBlock;
import com.gas.forecast.business.dto.AiChatErrorInfo;
import com.gas.forecast.business.dto.AiClarificationOption;
import com.gas.forecast.business.dto.AgentPendingOperation;
import com.gas.forecast.business.dto.PythonAgentChatRequest;
import com.gas.forecast.business.dto.PythonAgentChatResponse;
import com.gas.forecast.business.enums.AgentRunStatusEnum;
import com.gas.forecast.business.enums.AiAgentCodeEnum;
import com.gas.forecast.business.enums.AiChatInputTypeEnum;
import com.gas.forecast.business.enums.AiChatAgentStatusEnum;
import com.gas.forecast.business.enums.AiChatContentTypeEnum;
import com.gas.forecast.business.enums.AiClarificationInputTypeEnum;
import com.gas.forecast.business.enums.AiClarificationReasonEnum;
import com.gas.forecast.business.enums.AiConversationStatusEnum;
import com.gas.forecast.business.enums.AiInteractionTypeEnum;
import com.gas.forecast.business.enums.AiInteractionStatusEnum;
import com.gas.forecast.business.enums.AiMessageReferenceRelationEnum;
import com.gas.forecast.business.enums.AiMessageRoleEnum;
import com.gas.forecast.business.enums.ExecutionTargetStatusEnum;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.security.context.SecurityContextHolder;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.AgentRunTb;
import com.gas.forecast.dao.domain.AiConversationInteractionTb;
import com.gas.forecast.dao.domain.AiConversationMessageTb;
import com.gas.forecast.dao.domain.AiConversationTb;
import com.gas.forecast.dao.domain.AiMessageRefTb;
import com.gas.forecast.dao.domain.ExecutionTaskDetailTb;
import com.gas.forecast.dao.domain.ExecutionTaskTb;
import com.gas.forecast.dao.mapper.AgentRunTbMapper;
import com.gas.forecast.dao.mapper.AiConversationInteractionTbMapper;
import com.gas.forecast.dao.mapper.AiConversationMessageTbMapper;
import com.gas.forecast.dao.mapper.AiConversationTbMapper;
import com.gas.forecast.dao.mapper.AiMessageRefTbMapper;
import com.gas.forecast.dao.mapper.ExecutionTaskDetailTbMapper;
import com.gas.forecast.dao.mapper.ExecutionTaskTbMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Java 会话入口的数据库事务服务；远程 Python 调用不在数据库事务中执行。
 */
@Service
@RequiredArgsConstructor
public class AiChatPersistenceService {
    private final AiConversationTbMapper conversationMapper;
    private final AiConversationMessageTbMapper messageMapper;
    private final AiConversationInteractionTbMapper interactionMapper;
    private final AgentRunTbMapper runMapper;
    private final AiMessageRefTbMapper referenceMapper;
    private final ExecutionTaskTbMapper executionTaskMapper;
    private final ExecutionTaskDetailTbMapper executionTaskDetailMapper;
    private final ObjectMapper objectMapper;

    /**
     * 创建或加载会话，保存用户消息和 Agent Run，并生成发给 Python 的请求。
     */
    @Transactional
    public PythonAgentChatRequest createTurn(AiChatRequest request) {
        // 1. 统一记录本轮开始时间，保证消息、Run和会话更新时间一致。
        Date now = new Date();

        // 2. 首次对话时创建会话，后续对话加载已有会话，并校验会话归属及智能体。
        AiConversationTb conversation = requireOrCreateConversation(request, now);
        validateConversationRequest(conversation, request);

        // 3. 加载近期消息；结构化回复同时从交互表加载待恢复操作。
        List<PythonAgentChatRequest.RecentMessage> recentMessages = loadRecentMessages(conversation.getId(), 20);
        List<PythonAgentChatRequest.RecentForecastTask> recentForecastTasks = loadRecentForecastTasks(conversation.getConversationNo(), 5);
        AgentPendingOperation pendingOperation = validateAndLoadPendingOperation(conversation, request);

        // 5. 组合摘要、结构化记忆和近期消息，形成完整会话上下文。
        PythonAgentChatRequest.ConversationContext conversationContext = new PythonAgentChatRequest.ConversationContext(trimToNull(conversation.getSummary()), pendingOperation, recentMessages, recentForecastTasks);

        // 6. 创建本轮turnId并先保存用户消息；同轮助手消息完成后继续使用该turnId。
        String turnId = "TURN-" + compactUuid();
        AiConversationMessageTb userMessage = new AiConversationMessageTb();
        userMessage.setConversationId(conversation.getId());
        userMessage.setRole(AiMessageRoleEnum.USER.getCode());
        userMessage.setTurnId(turnId);
        userMessage.setContent(request.getMessage().trim());
        userMessage.setCreatedAt(now);
        messageMapper.insert(userMessage);

        // 7. 创建runNo并组装发给Python Agent的内部请求，所有服务端编号均由Java生成。
        String runNo = "RUN-" + compactUuid();
        AiChatRequest.BusinessContext context = request.getBusinessContext() == null ? new AiChatRequest.BusinessContext(null, null, null, null, null, null) : request.getBusinessContext();
        PythonAgentChatRequest agentRequest = new PythonAgentChatRequest(conversation.getConversationNo(), turnId, runNo, request.getMessage().trim(), request.getInputType().name(), trimToNull(request.getInteractionId()), trimToNull(request.getReplyToTurnId()), conversation.getAgentCode(), context, conversationContext);

        // 8. 保存Agent Run及请求快照，用于跟踪本轮执行状态、排查问题和校验Python响应。
        AgentRunTb run = new AgentRunTb();
        run.setRunNo(runNo);
        run.setConversationId(conversation.getId());
        run.setMessageId(userMessage.getId());
        run.setAgentCode(conversation.getAgentCode());
        run.setStatus(AgentRunStatusEnum.RUNNING.getCode());
        run.setRequestJson(agentRequestSnapshot(agentRequest));
        run.setStartedAt(now);
        run.setCreatedAt(now);
        run.setUpdatedAt(now);
        runMapper.insert(run);

        // 9. 更新会话最后活跃时间并返回内部请求；远程Python调用在本事务提交后执行。
        conversation.setLastMessageAt(now);
        conversation.setUpdatedAt(now);
        conversationMapper.updateById(conversation);
        return agentRequest;
    }

    /**
     * 保存 Python 执行结果、助手消息及消息业务引用。
     */
    @Transactional
    public AiChatResponse complete(PythonAgentChatResponse result) {
        // 1. 根据Python回传的runNo加载本轮Run和所属会话，并校验响应确实对应本轮用户消息。
        AgentRunTb run = requireRun(result.runNo());
        AiConversationTb conversation = requireOwnedConversation(run.getConversationId());
        AiConversationMessageTb userMessage = verifyResultIdentity(run, conversation, result);

        // 2. 规范化运行状态、回复正文、业务引用和澄清问题，组装最终返回前端的响应对象。
        // 将Python状态转换为Java支持的SUCCESS、WAITING或FAILED。
        String status = normalizeRunStatus(result.status().name());

        // 从MARKDOWN内容块提取消息正文，用于保存ai_conversation_message_tb.content。
        String answer = markdownText(result.blocks());

        // 从TASK_CARD内容块提取任务编号，用于保存消息与执行任务的关联关系。
        List<AiChatResponse.Reference> references = referencesFromBlocks(result.blocks());

        // 仅在WAITING状态下接收并校验Python返回的当前澄清问题。
        PythonAgentChatResponse.PythonClarification clarification = normalizeClarification(status, result.clarification());

        // Python只返回精简业务快照；正式interactionId由Java单独生成并保存在交互表。
        AgentPendingOperation pendingOperation = clarification == null ? null : clarification.getPendingOperation();

        // 将Python内部澄清对象转换为返回Vue的简化澄清对象。
        AiChatResponse.FrontendClarification frontendClarification = clarification == null ? null : new AiChatResponse.FrontendClarification("INT-" + compactUuid(), clarification.getField(), clarification.getInputType(), clarification.getOptions());

        // 校验内容块类型和数据格式，然后转换为Java对外响应使用的内容块。
        List<AiChatContentBlock> blocks = normalizeContentBlocks(result.blocks());

        // 只有FAILED状态才向前端返回稳定错误码。
        AiChatErrorInfo error = AgentRunStatusEnum.FAILED.getCode().equals(status) && result.error() != null ? new AiChatErrorInfo(trimToNull(result.error().getCode())) : null;

        // 组合最终响应；该对象既返回Vue，也作为助手消息的结构化内容保存。
        AiChatResponse response = new AiChatResponse(conversation.getConversationNo(), result.turnId(), run.getRunNo(), conversation.getAgentCode(), AiChatAgentStatusEnum.valueOf(status), blocks, frontendClarification, error);

        // 3. 保存本轮助手消息；结构化响应同时落库，便于历史消息完整恢复。
        Date now = new Date();
        AiConversationMessageTb assistant = new AiConversationMessageTb();
        assistant.setConversationId(conversation.getId());
        assistant.setRole(AiMessageRoleEnum.ASSISTANT.getCode());
        assistant.setTurnId(result.turnId());
        assistant.setContent(answer);
        assistant.setStructuredJson(writeJson(response));
        assistant.setCreatedAt(now);
        messageMapper.insert(assistant);

        // 4. 保存本轮助手消息关联的任务编号；普通聊天或澄清消息通常没有业务引用。
        saveReferences(assistant.getId(), references, now);

        // 5. 如果本轮是对上一条澄清的回答，先结束旧交互；如果Python仍要求补充信息，再新增下一条WAITING交互。
        AiConversationInteractionTb answeredInteraction = completeInteractionReply(run, userMessage, status, now);
        savePendingInteraction(conversation, assistant, frontendClarification, pendingOperation, answeredInteraction, now);

        // 6. 保存Python本轮识别结果、执行摘要和错误信息，并将Agent Run更新为最终状态。
        run.setStatus(status);
        run.setIntentCode(null);
        run.setIntentJson(null);
        run.setSkillCode(null);
        run.setResultJson(runResultJson(result, response));
        run.setErrorCode(error == null ? null : error.getCode());
        run.setErrorMessage(error == null ? null : answer);
        run.setFinishedAt(now);
        run.setUpdatedAt(now);
        runMapper.updateById(run);

        // 7. 更新会话最后活跃时间，返回已经完成持久化的前端响应。
        conversation.setLastMessageAt(now);
        conversation.setUpdatedAt(now);
        conversationMapper.updateById(conversation);
        return response;
    }

    /**
     * Python 不可用或内部接口调用失败时，可靠地结束 Run 并保存可见失败消息。
     */
    @Transactional
    public AiChatResponse fail(PythonAgentChatRequest request, RuntimeException exception) {
        String message = "智能体服务暂时不可用，本轮未确认创建新的业务任务，请稍后重试。";
        AiChatContentBlock.MarkdownContentData markdown = new AiChatContentBlock.MarkdownContentData();
        markdown.setContent(message);
        PythonAgentChatResponse failure = new PythonAgentChatResponse(request.conversationNo(), request.turnId(), request.runNo(), request.agentCode(), AiChatAgentStatusEnum.FAILED, List.of(new AiChatContentBlock(AiChatContentTypeEnum.MARKDOWN, markdown)), null, new AiChatErrorInfo("AGENT_SERVICE_ERROR"));
        return complete(failure);
    }

    private AiConversationTb requireOrCreateConversation(AiChatRequest request, Date now) {
        if (TextUtils.hasText(request.getConversationNo())) {
            AiConversationTb existing = conversationMapper.selectOne(Wrappers.<AiConversationTb>lambdaQuery().eq(AiConversationTb::getConversationNo, request.getConversationNo().trim()).eq(AiConversationTb::getCreatedBy, currentUserId()).eq(AiConversationTb::getDeletedFlag, 0).last("limit 1"));
            if (existing == null) {
                throw new BusinessException("会话不存在或无权访问");
            }
            if (!AiConversationStatusEnum.ACTIVE.getCode().equalsIgnoreCase(existing.getStatus())) {
                throw new BusinessException("当前会话已归档，不能继续发送消息");
            }
            return existing;
        }

        String agentCode = TextUtils.hasText(request.getAgentCode()) ? request.getAgentCode().trim() : AiAgentCodeEnum.GENERAL.getCode();
        if (!AiAgentCodeEnum.contains(agentCode)) {
            throw new BusinessException("不支持的智能体编码：" + agentCode);
        }
        AiConversationTb conversation = new AiConversationTb();
        conversation.setConversationNo("AICHAT-" + compactUuid());
        conversation.setTitle(abbreviate(request.getMessage().trim(), 200));
        conversation.setAgentCode(agentCode);
        conversation.setStatus(AiConversationStatusEnum.ACTIVE.getCode());
        conversation.setDeletedFlag(0);
        conversation.setLastMessageAt(now);
        conversation.setCreatedBy(currentUserId());
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);
        conversationMapper.insert(conversation);
        return conversation;
    }

    private void validateConversationRequest(AiConversationTb conversation, AiChatRequest request) {
        if (TextUtils.hasText(request.getAgentCode()) && !Objects.equals(conversation.getAgentCode(), request.getAgentCode().trim())) {
            throw new BusinessException("已有会话不能切换智能体");
        }
    }

    /**
     * 校验结构化回复只消费当前会话最新、类型匹配的待处理交互。
     */
    private AgentPendingOperation validateAndLoadPendingOperation(AiConversationTb conversation, AiChatRequest request) {
        if (request.getInputType() == AiChatInputTypeEnum.TEXT) {
            return null;
        }
        if (!TextUtils.hasText(request.getConversationNo())) {
            throw new BusinessException("结构化回复必须属于一个已有会话");
        }
        if (!TextUtils.hasText(request.getInteractionId()) || !TextUtils.hasText(request.getReplyToTurnId())) {
            throw new BusinessException("结构化回复必须提供interactionId和replyToTurnId");
        }
        AiConversationInteractionTb stored = interactionMapper.selectOne(Wrappers.<AiConversationInteractionTb>lambdaQuery().eq(AiConversationInteractionTb::getConversationId, conversation.getId()).eq(AiConversationInteractionTb::getInteractionId, request.getInteractionId().trim()).eq(AiConversationInteractionTb::getStatus, AiInteractionStatusEnum.WAITING.getCode()).last("limit 1"));
        if (stored == null) {
            throw new BusinessException("当前澄清交互不存在、已回答或已过期");
        }
        if (!Objects.equals(stored.getQuestionTurnId(), request.getReplyToTurnId().trim())) {
            throw new BusinessException("交互回复对应的提问轮次不一致");
        }
        AgentPendingOperation pendingOperation = readPendingOperation(stored.getOperationJson());
        if (pendingOperation == null) {
            throw new BusinessException("当前会话没有等待处理的澄清或确认");
        }
        String expectedType = request.getInputType() == AiChatInputTypeEnum.CLARIFICATION_REPLY ? AiInteractionTypeEnum.CLARIFICATION.getCode() : AiInteractionTypeEnum.CONFIRMATION.getCode();
        if (!Objects.equals(expectedType, stored.getInteractionType())) {
            throw new BusinessException("回复类型与交互记录不匹配");
        }
        if (AiInteractionTypeEnum.CONFIRMATION.getCode().equals(expectedType) && !TextUtils.hasText(pendingOperation.getAction())) {
            throw new BusinessException("当前没有可确认或取消的待执行动作");
        }
        return pendingOperation;
    }

    /**
     * 加载最近的原始消息；较早消息后续由会话摘要承载。
     */
    private List<PythonAgentChatRequest.RecentMessage> loadRecentMessages(Long conversationId, int limit) {
        // 1. 按消息ID倒序查询最近limit条消息，避免把整个会话历史都发送给Python。
        List<AiConversationMessageTb> messages = new ArrayList<>(messageMapper.selectList(Wrappers.<AiConversationMessageTb>lambdaQuery().eq(AiConversationMessageTb::getConversationId, conversationId).orderByDesc(AiConversationMessageTb::getId).last("limit " + limit)));

        // 2. 数据库查询结果是从新到旧；发送给Python前恢复成从旧到新的正常对话顺序。
        Collections.reverse(messages);

        // 3. 当前会话没有历史消息时直接返回，避免继续查询消息引用和执行任务。
        if (messages.isEmpty()) {
            return List.of();
        }

        // 4. Python理解上下文只需要角色和正文，不传数据库主键、时间及任务引用。
        List<PythonAgentChatRequest.RecentMessage> result = new ArrayList<>();
        for (AiConversationMessageTb message : messages) {
            if (!TextUtils.hasText(message.getRole()) || !TextUtils.hasText(message.getContent())) {
                continue;
            }
            result.add(new PythonAgentChatRequest.RecentMessage(message.getRole(), message.getContent()));
        }
        return result;
    }

    /**
     * 加载当前会话最近的预测任务和成功明细，供Python解析“刚才的预测”。
     *
     * <p>
     * 批量任务保留所有成功明细，不在Java侧静默挑选第一个预测结果。
     * </p>
     */
    private List<PythonAgentChatRequest.RecentForecastTask> loadRecentForecastTasks(String conversationNo, int limit) {
        List<ExecutionTaskTb> tasks = executionTaskMapper.selectList(Wrappers.<ExecutionTaskTb>lambdaQuery().eq(ExecutionTaskTb::getConversationNo, conversationNo).orderByDesc(ExecutionTaskTb::getId).last("limit " + limit));
        if (tasks.isEmpty()) {
            return List.of();
        }
        List<PythonAgentChatRequest.RecentForecastTask> result = new ArrayList<>();
        for (ExecutionTaskTb task : tasks) {
            List<ExecutionTaskDetailTb> details = executionTaskDetailMapper.selectList(Wrappers.<ExecutionTaskDetailTb>lambdaQuery().eq(ExecutionTaskDetailTb::getTaskNo, task.getTaskNo()).eq(ExecutionTaskDetailTb::getStatus, ExecutionTargetStatusEnum.SUCCESS.getCode()).isNotNull(ExecutionTaskDetailTb::getForecastNo).orderByAsc(ExecutionTaskDetailTb::getId));
            List<PythonAgentChatRequest.ForecastDetail> forecasts = details.stream().filter(detail -> TextUtils.hasText(detail.getForecastNo())).map(detail -> new PythonAgentChatRequest.ForecastDetail(detail.getDetailNo(), detail.getForecastNo(), detail.getAgentCode(), detail.getStatus())).toList();
            if (!forecasts.isEmpty()) {
                result.add(new PythonAgentChatRequest.RecentForecastTask(task.getTaskNo(), task.getStatus(), forecasts));
            }
        }
        return List.copyOf(result);
    }

    private AgentPendingOperation readPendingOperation(String json) {
        if (!TextUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, AgentPendingOperation.class);
        } catch (Exception exception) {
            throw new IllegalStateException("待处理操作快照格式错误", exception);
        }
    }

    private String textValue(JsonNode object, String fieldName) {
        JsonNode value = object == null ? null : object.get(fieldName);
        return value != null && value.isTextual() ? value.textValue() : null;
    }

    /**
     * 校验 Python 响应是否确实属于 Java 本次发出的 Agent 请求。
     *
     * <p>
     * conversationNo 和 turnId 必须与 runNo 对应的数据库记录一致，防止 Python 响应被保存到其他会话或其他轮次。
     * </p>
     *
     * @param run
     *            Java 调用 Python 前创建的 Agent Run
     * @param conversation
     *            Agent Run 所属会话
     * @param result
     *            Python 返回的内部 Agent 执行结果
     * @throws IllegalStateException
     *             Python 响应标识与 Java 原请求不一致时抛出
     */
    private AiConversationMessageTb verifyResultIdentity(AgentRunTb run, AiConversationTb conversation, PythonAgentChatResponse result) {
        AiConversationMessageTb user = loadAndValidateRunUserMessage(run);
        boolean valid = Objects.equals(conversation.getConversationNo(), result.conversationNo()) && Objects.equals(user.getTurnId(), result.turnId());
        if (!valid) {
            throw new IllegalStateException("Python Agent 响应标识与 Java 请求不一致");
        }
        return user;
    }

    /**
     * 获取触发 Agent Run 的用户消息，并校验 Run 与消息之间的数据库关联完整性。
     *
     * <p>
     * 消息不存在、消息属于其他会话或消息角色不是 USER，均说明数据库关联错误。
     * </p>
     *
     * @param run
     *            需要读取触发消息的 Agent Run
     * @return 与 run.messageId 对应且已通过关联校验的用户消息
     * @throws IllegalStateException
     *             Run 与用户消息的数据库关系无效时抛出
     */
    private AiConversationMessageTb loadAndValidateRunUserMessage(AgentRunTb run) {
        // run.messageId 保存的是创建 Run 前已经落库的用户消息主键。
        AiConversationMessageTb user = messageMapper.selectById(run.getMessageId());
        if (user == null) {
            throw new IllegalStateException("Agent Run 关联的用户消息不存在，runNo=" + run.getRunNo());
        }

        // 防止 messageId 错误地指向了另一个会话中的消息。
        if (!Objects.equals(user.getConversationId(), run.getConversationId())) {
            throw new IllegalStateException("Agent Run 与用户消息所属会话不一致，runNo=" + run.getRunNo());
        }

        // Agent Run 只能由 USER 消息触发，不能关联助手、系统或工具消息。
        if (!AiMessageRoleEnum.USER.getCode().equalsIgnoreCase(user.getRole())) {
            throw new IllegalStateException("Agent Run 关联的消息不是用户消息，runNo=" + run.getRunNo());
        }
        return user;
    }

    /**
     * 将本轮用户消息登记为上一条待处理交互的回答。
     *
     * <p>
     * Agent调用失败时保留WAITING供用户重试；其余已处理的回复结束原交互。 本轮若仍需补充信息，会另行插入一条新的WAITING记录。
     * </p>
     */
    private AiConversationInteractionTb completeInteractionReply(AgentRunTb run, AiConversationMessageTb userMessage, String runStatus, Date now) {
        String interactionId = runRequestText(run, "interactionId");
        if (!TextUtils.hasText(interactionId)) {
            return null;
        }
        AiConversationInteractionTb interaction = interactionMapper.selectOne(Wrappers.<AiConversationInteractionTb>lambdaQuery().eq(AiConversationInteractionTb::getConversationId, run.getConversationId()).eq(AiConversationInteractionTb::getInteractionId, interactionId).last("limit 1"));
        if (interaction == null) {
            throw new IllegalStateException("Agent Run关联的交互记录不存在：" + interactionId);
        }
        if (AgentRunStatusEnum.FAILED.getCode().equals(runStatus)) {
            return interaction;
        }
        if (!AiInteractionStatusEnum.WAITING.getCode().equals(interaction.getStatus())) {
            throw new IllegalStateException("Agent Run关联的交互已经结束：" + interactionId);
        }
        interaction.setAnswerTurnId(userMessage.getTurnId());
        interaction.setAnswerMessageId(userMessage.getId());
        interaction.setStatus(AgentRunStatusEnum.CANCELLED.getCode().equals(runStatus) ? AiInteractionStatusEnum.CANCELLED.getCode() : AiInteractionStatusEnum.ANSWERED.getCode());
        interaction.setAnsweredAt(now);
        interaction.setUpdatedAt(now);
        interactionMapper.updateById(interaction);
        return interaction;
    }

    /**
     * 保存本轮新产生的待澄清或待确认问题；每次提问新增记录，不覆盖历史。
     */
    private void savePendingInteraction(AiConversationTb conversation, AiConversationMessageTb assistantMessage, AiChatResponse.FrontendClarification clarification, AgentPendingOperation operation, AiConversationInteractionTb answeredInteraction, Date now) {
        if (clarification == null) {
            return;
        }
        Long duplicate = interactionMapper.selectCount(Wrappers.<AiConversationInteractionTb>lambdaQuery().eq(AiConversationInteractionTb::getInteractionId, clarification.getInteractionId()));
        if (duplicate != null && duplicate > 0) {
            throw new IllegalStateException("Java重复生成交互编号：" + clarification.getInteractionId());
        }
        if (operation == null || !TextUtils.hasText(operation.getOriginalMessage()) || operation.getIssues().isEmpty()) {
            throw new IllegalStateException("待澄清响应缺少有效的pendingOperation");
        }
        AiConversationInteractionTb interaction = new AiConversationInteractionTb();
        interaction.setInteractionId(clarification.getInteractionId());
        interaction.setConversationId(conversation.getId());
        interaction.setSourceTurnId(answeredInteraction == null ? assistantMessage.getTurnId() : answeredInteraction.getSourceTurnId());
        interaction.setQuestionTurnId(assistantMessage.getTurnId());
        interaction.setQuestionMessageId(assistantMessage.getId());
        AgentPendingOperation.ResolutionIssue issue = operation.getIssues().stream().filter(value -> value != null && Objects.equals(value.field(), clarification.getField())).findFirst().orElseThrow(() -> new IllegalStateException("待澄清问题与pendingOperation中的issues不一致"));
        interaction.setInteractionType(AiClarificationInputTypeEnum.CONFIRM.equals(issue.inputType()) ? AiInteractionTypeEnum.CONFIRMATION.getCode() : AiInteractionTypeEnum.CLARIFICATION.getCode());
        interaction.setReasonCode(issue.reason().getCode());
        interaction.setQuestionJson(writeJson(clarification));
        interaction.setOperationJson(writeJson(operation));
        interaction.setStatus(AiInteractionStatusEnum.WAITING.getCode());
        interaction.setCreatedAt(now);
        interaction.setUpdatedAt(now);
        interactionMapper.insert(interaction);
    }

    /**
     * 从Agent Run的请求快照读取稳定字符串字段。
     */
    private String runRequestText(AgentRunTb run, String fieldName) {
        if (!TextUtils.hasText(run.getRequestJson())) {
            return null;
        }
        try {
            return trimToNull(textValue(objectMapper.readTree(run.getRequestJson()), fieldName));
        } catch (Exception exception) {
            throw new IllegalStateException("Agent Run请求快照格式错误，runNo=" + run.getRunNo(), exception);
        }
    }

    private void saveReferences(Long messageId, List<AiChatResponse.Reference> references, Date now) {
        for (AiChatResponse.Reference reference : references) {
            AiMessageRefTb entity = new AiMessageRefTb();
            entity.setMessageId(messageId);
            entity.setTaskNo(reference.taskNo());
            entity.setRelationType(reference.relationType());
            entity.setCreatedAt(now);
            referenceMapper.insert(entity);
        }
    }

    private List<AiChatResponse.Reference> normalizeReferences(List<AiChatResponse.Reference> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, AiChatResponse.Reference> unique = new LinkedHashMap<>();
        for (AiChatResponse.Reference value : values) {
            if (value == null || !AiMessageReferenceRelationEnum.contains(value.relationType()) || !TextUtils.hasText(value.taskNo())) {
                throw new IllegalStateException("Python Agent 返回了无效的业务对象引用");
            }
            String number = value.taskNo().trim();
            if (number.length() > 64) {
                throw new IllegalStateException("Python Agent 返回的业务对象编号过长");
            }
            AiChatResponse.Reference normalized = new AiChatResponse.Reference(number, value.relationType(), trimToNull(value.title()), trimToNull(value.status()));
            unique.put(number + "\u0000" + value.relationType(), normalized);
        }
        if (unique.size() > 1) {
            throw new IllegalStateException("Python Agent 一条消息只能关联一个执行任务");
        }
        return List.copyOf(unique.values());
    }

    /** TASK_CARD既是前端展示块，也是消息与执行任务建立关联的唯一来源。 */
    private List<AiChatResponse.Reference> referencesFromBlocks(List<AiChatContentBlock> blocks) {
        if (blocks == null) {
            return List.of();
        }
        List<AiChatResponse.Reference> references = new ArrayList<>();
        for (AiChatContentBlock block : blocks) {
            if (block == null || block.getType() != AiChatContentTypeEnum.TASK_CARD || !(block.getData() instanceof AiChatContentBlock.TaskCardContentData card)) {
                continue;
            }
            references.add(new AiChatResponse.Reference(card.getTaskNo(), card.getRelationType(), card.getTitle(), card.getStatus()));
        }
        return normalizeReferences(references);
    }

    private String markdownText(List<AiChatContentBlock> blocks) {
        String text = blocks == null ? "" : blocks.stream().filter(block -> block != null && block.getType() == AiChatContentTypeEnum.MARKDOWN && block.getData() instanceof AiChatContentBlock.MarkdownContentData).map(block -> ((AiChatContentBlock.MarkdownContentData) block.getData()).getContent()).filter(TextUtils::hasText).findFirst().orElse("");
        return TextUtils.hasText(text) ? text.trim() : "AI 未返回有效内容";
    }

    private List<AiChatContentBlock> normalizeContentBlocks(List<AiChatContentBlock> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalStateException("Python Agent未返回内容块");
        }
        for (AiChatContentBlock value : values) {
            if (value == null || value.getType() == null || value.getData() == null) {
                throw new IllegalStateException("Python Agent返回了无效内容块");
            }
        }
        return List.copyOf(values);
    }

    /** WAITING响应必须携带一个问题及完整恢复快照。 */
    private PythonAgentChatResponse.PythonClarification normalizeClarification(String status, PythonAgentChatResponse.PythonClarification value) {
        if (!AgentRunStatusEnum.WAITING.getCode().equals(status)) {
            return null;
        }
        if (value == null || !TextUtils.hasText(value.getField()) || !AiClarificationReasonEnum.contains(value.getReason()) || !AiClarificationInputTypeEnum.contains(value.getInputType()) || value.getPendingOperation() == null || !TextUtils.hasText(value.getPendingOperation().getOriginalMessage()) || value.getPendingOperation().getIssues().isEmpty()) {
            throw new IllegalStateException("Python Agent返回了不完整的澄清问题");
        }
        if ((AiClarificationInputTypeEnum.SELECT.getCode().equals(value.getInputType()) || AiClarificationInputTypeEnum.MULTI_SELECT.getCode().equals(value.getInputType()) || AiClarificationInputTypeEnum.CONFIRM.getCode().equals(value.getInputType())) && value.getOptions().isEmpty()) {
            throw new IllegalStateException("选择类澄清必须提供候选项");
        }
        for (AiClarificationOption option : value.getOptions()) {
            if (option == null || !TextUtils.hasText(option.getValue()) || !TextUtils.hasText(option.getLabel())) {
                throw new IllegalStateException("Python Agent返回了无效候选项");
            }
        }
        return value;
    }

    private AgentRunTb requireRun(String runNo) {
        AgentRunTb run = runMapper.selectOne(Wrappers.<AgentRunTb>lambdaQuery().eq(AgentRunTb::getRunNo, runNo).last("limit 1"));
        if (run == null) {
            throw new IllegalStateException("Agent Run 不存在：" + runNo);
        }
        return run;
    }

    private AiConversationTb requireOwnedConversation(Long conversationId) {
        AiConversationTb conversation = conversationMapper.selectOne(Wrappers.<AiConversationTb>lambdaQuery().eq(AiConversationTb::getId, conversationId).eq(AiConversationTb::getCreatedBy, currentUserId()).eq(AiConversationTb::getDeletedFlag, 0).last("limit 1"));
        if (conversation == null) {
            throw new BusinessException("会话不存在或无权访问");
        }
        return conversation;
    }

    private String agentRequestSnapshot(PythonAgentChatRequest request) {
        ObjectNode value = objectMapper.createObjectNode();
        value.put("conversationNo", request.conversationNo());
        value.put("turnId", request.turnId());
        value.put("runNo", request.runNo());
        value.put("message", request.message());
        value.put("inputType", request.inputType());
        value.put("interactionId", request.interactionId());
        value.put("replyToTurnId", request.replyToTurnId());
        value.put("agentCode", request.agentCode());
        value.set("businessContext", objectMapper.valueToTree(request.businessContext()));
        ObjectNode context = value.putObject("conversationContext");
        context.put("summary", request.conversationContext().summary());
        context.set("pendingOperation", objectMapper.valueToTree(request.conversationContext().pendingOperation()));
        context.put("recentMessageCount", request.conversationContext().recentMessages().size());
        return writeJson(value);
    }

    private String runResultJson(PythonAgentChatResponse result, AiChatResponse response) {
        ObjectNode stored = objectMapper.createObjectNode();
        stored.set("response", objectMapper.valueToTree(response));
        return writeJson(stored);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("会话结构化数据序列化失败", exception);
        }
    }

    private String normalizeRunStatus(String status) {
        return AgentRunStatusEnum.normalize(status);
    }

    private String currentUserId() {
        Long userId = SecurityContextHolder.getUserId();
        if (userId == null) {
            throw new BusinessException("登录状态已失效");
        }
        return String.valueOf(userId);
    }

    private String compactUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String trimToNull(String value) {
        return TextUtils.hasText(value) ? value.trim() : null;
    }

    private String abbreviate(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit);
    }

}
