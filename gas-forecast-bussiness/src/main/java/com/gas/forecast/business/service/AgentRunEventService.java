package com.gas.forecast.business.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.business.dto.AgentRunEventCreateRequest;
import com.gas.forecast.business.dto.AgentRunEventResponse;
import com.gas.forecast.business.dto.AgentRunEventUpdateRequest;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.security.context.SecurityContextHolder;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.AgentRunEventTb;
import com.gas.forecast.dao.domain.AgentRunTb;
import com.gas.forecast.dao.domain.AiConversationTb;
import com.gas.forecast.dao.mapper.AgentRunEventTbMapper;
import com.gas.forecast.dao.mapper.AgentRunTbMapper;
import com.gas.forecast.dao.mapper.AiConversationTbMapper;
import java.util.Date;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Agent 过程事件的创建、更新和历史恢复；所有操作均校验会话归属。 */
@Service
@RequiredArgsConstructor
public class AgentRunEventService {
    private static final List<String> STATUSES = List.of("RUNNING", "SUCCESS", "FAILED", "INTERRUPTED", "SKIPPED", "CANCELLED");
    private final AgentRunTbMapper runMapper;
    private final AgentRunEventTbMapper eventMapper;
    private final AiConversationTbMapper conversationMapper;

    @Transactional
    public AgentRunEventResponse create(String runNo, AgentRunEventCreateRequest request) {
        requireOwnedRun(runNo);
        AgentRunEventTb event = new AgentRunEventTb();
        event.setRunNo(runNo);
        event.setEventCode(request.getEventCode().trim());
        event.setMessage(request.getMessage().trim());
        event.setEventDataJson(trimToNull(request.getEventDataJson()));
        event.setStatus(normalizeStatus(request.getStatus(), "RUNNING"));
        event.setForecastNo(trimToNull(request.getForecastNo()));
        event.setTaskDetailNo(trimToNull(request.getTaskDetailNo()));
        Date now = new Date();
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        eventMapper.insert(event);
        return toResponse(event);
    }

    @Transactional
    public AgentRunEventResponse update(String runNo, Long eventId, AgentRunEventUpdateRequest request) {
        requireOwnedRun(runNo);
        AgentRunEventTb event = eventMapper.selectOne(Wrappers.<AgentRunEventTb>lambdaQuery()
                .eq(AgentRunEventTb::getId, eventId).eq(AgentRunEventTb::getRunNo, runNo).last("limit 1"));
        if (event == null) throw new BusinessException("运行事件不存在或不属于当前运行");
        event.setStatus(normalizeStatus(request.getStatus(), null));
        event.setMessage(request.getMessage().trim());
        event.setEventDataJson(trimToNull(request.getEventDataJson()));
        event.setErrorCode(trimToNull(request.getErrorCode()));
        event.setErrorMessage(trimToNull(request.getErrorMessage()));
        event.setUpdatedAt(new Date());
        eventMapper.updateById(event);
        return toResponse(event);
    }

    public List<AgentRunEventResponse> list(String runNo) {
        requireOwnedRun(runNo);
        return listByRunNo(runNo);
    }

    /** 供异步聊天 SSE 转发真实过程事件；运行已由当前聊天请求创建并校验过。 */
    public List<AgentRunEventResponse> listInternal(String runNo) {
        requireRun(runNo);
        return listByRunNo(runNo);
    }

    private List<AgentRunEventResponse> listByRunNo(String runNo) {
        return eventMapper.selectList(Wrappers.<AgentRunEventTb>lambdaQuery()
                .eq(AgentRunEventTb::getRunNo, runNo).orderByAsc(AgentRunEventTb::getId))
                .stream().map(this::toResponse).toList();
    }

    /**
     * 供本服务内的异步业务执行器记录事件。运行归属已在任务创建时确定，后台线程
     * 没有用户安全上下文，因此这里只确认 run 存在，不重复做前端访问权限校验。
     */
    @Transactional
    public Long startInternal(String runNo, String eventCode, String message, String forecastNo, String taskDetailNo) {
        return startInternal(runNo, eventCode, message, null, forecastNo, taskDetailNo);
    }

    @Transactional
    public Long startInternal(String runNo, String eventCode, String message, String eventDataJson, String forecastNo, String taskDetailNo) {
        if (!TextUtils.hasText(runNo)) return null;
        requireRun(runNo);
        AgentRunEventTb event = new AgentRunEventTb();
        event.setRunNo(runNo.trim());
        event.setEventCode(eventCode);
        event.setMessage(message);
        event.setEventDataJson(trimToNull(eventDataJson));
        event.setStatus("RUNNING");
        event.setForecastNo(trimToNull(forecastNo));
        event.setTaskDetailNo(trimToNull(taskDetailNo));
        Date now = new Date();
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        eventMapper.insert(event);
        return event.getId();
    }

    @Transactional
    public void completeInternal(Long eventId, boolean success, String message, String errorCode, String errorMessage) {
        completeInternal(eventId, success, message, null, errorCode, errorMessage);
    }

    @Transactional
    public void completeInternal(Long eventId, boolean success, String message, String eventDataJson, String errorCode, String errorMessage) {
        updateInternal(eventId, success ? "SUCCESS" : "FAILED", message, eventDataJson, errorCode, errorMessage);
    }

    /**
     * 供模型/业务服务在同一运行事件内上送真实进度或心跳。
     * RUNNING 更新只刷新当前状态和 updated_at，不会追加冗余事件记录。
     */
    @Transactional
    public void updateInternal(Long eventId, String status, String message, String eventDataJson,
            String errorCode, String errorMessage) {
        if (eventId == null) return;
        AgentRunEventTb event = eventMapper.selectById(eventId);
        if (event == null) return;
        event.setStatus(normalizeStatus(status, "RUNNING"));
        event.setMessage(message);
        if (eventDataJson != null) event.setEventDataJson(trimToNull(eventDataJson));
        event.setErrorCode(trimToNull(errorCode));
        event.setErrorMessage(trimToNull(errorMessage));
        event.setUpdatedAt(new Date());
        eventMapper.updateById(event);
    }

    private AgentRunTb requireOwnedRun(String runNo) {
        AgentRunTb run = requireRun(runNo);
        AiConversationTb conversation = conversationMapper.selectOne(Wrappers.<AiConversationTb>lambdaQuery()
                .eq(AiConversationTb::getId, run.getConversationId())
                .eq(AiConversationTb::getCreatedBy, currentUserId())
                .eq(AiConversationTb::getDeletedFlag, 0).last("limit 1"));
        if (conversation == null) throw new BusinessException("智能体运行不存在或无权访问");
        return run;
    }

    private AgentRunTb requireRun(String runNo) {
        AgentRunTb run = runMapper.selectOne(Wrappers.<AgentRunTb>lambdaQuery()
                .eq(AgentRunTb::getRunNo, runNo).last("limit 1"));
        if (run == null) throw new BusinessException("智能体运行不存在");
        return run;
    }

    private String normalizeStatus(String value, String defaultValue) {
        String status = TextUtils.hasText(value) ? value.trim().toUpperCase() : defaultValue;
        if (!STATUSES.contains(status)) throw new BusinessException("不支持的事件状态：" + status);
        return status;
    }
    private String trimToNull(String value) { return TextUtils.hasText(value) ? value.trim() : null; }
    private String currentUserId() {
        Long userId = SecurityContextHolder.getUserId();
        if (userId == null) throw new BusinessException("登录状态已失效");
        return String.valueOf(userId);
    }
    private AgentRunEventResponse toResponse(AgentRunEventTb event) {
        return new AgentRunEventResponse(event.getId(), event.getRunNo(), event.getEventCode(), event.getMessage(), event.getEventDataJson(),
                event.getStatus(), event.getForecastNo(), event.getTaskDetailNo(), event.getErrorCode(),
                event.getErrorMessage(), event.getCreatedAt(), event.getUpdatedAt());
    }
}
