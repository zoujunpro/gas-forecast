package com.gas.forecast.business.service;

import com.gas.forecast.common.util.TextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Java 业务层统一过程事件记录器。
 *
 * <p>业务服务只描述自己实际开始和完成的操作；事件记录异常不能影响主业务。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRunEventRecorder {
    private final AgentRunEventService eventService;

    public Long start(String runNo, String eventCode, String message, String forecastNo, String taskDetailNo) {
        if (!TextUtils.hasText(runNo)) return null;
        try {
            return eventService.startInternal(runNo, eventCode, message, forecastNo, taskDetailNo);
        } catch (RuntimeException exception) {
            log.warn("event=agent_run_event_record_start_failed run_no={} event_code={}", runNo, eventCode, exception);
            return null;
        }
    }

    public void success(Long eventId, String message) {
        complete(eventId, true, message, null, null, null);
    }

    /** 完成时一并保存真实校验/模型指标，供前端展开事件查看。 */
    public void success(Long eventId, String message, String eventDataJson) {
        complete(eventId, true, message, eventDataJson, null, null);
    }

    /** 更新同一条运行事件的真实进展，同时作为长任务心跳刷新。 */
    public void progress(Long eventId, String message, String eventDataJson) {
        if (eventId == null) return;
        try {
            eventService.updateInternal(eventId, "RUNNING", message, eventDataJson, null, null);
        } catch (RuntimeException exception) {
            log.warn("event=agent_run_event_record_progress_failed event_id={}", eventId, exception);
        }
    }

    public void failure(Long eventId, String message, String errorCode, Throwable error) {
        complete(eventId, false, message, null, errorCode, error == null ? null : error.getMessage());
    }

    private void complete(Long eventId, boolean success, String message, String eventDataJson,
            String errorCode, String errorMessage) {
        if (eventId == null) return;
        try {
            eventService.completeInternal(eventId, success, message, eventDataJson, errorCode, errorMessage);
        } catch (RuntimeException exception) {
            log.warn("event=agent_run_event_record_complete_failed event_id={}", eventId, exception);
        }
    }
}
