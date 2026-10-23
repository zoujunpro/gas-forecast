package com.gas.forecast.business.service;

import com.gas.forecast.dao.domain.ExecutionStepTb;
import com.gas.forecast.dao.domain.ExecutionTaskDetailTb;
import java.util.Map;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 执行步骤的实时通知；执行步骤表始终是页面刷新后的事实快照。 */
public interface ExecutionTaskEventService {
    SseEmitter subscribe(String taskNo);
    void publishStep(ExecutionTaskDetailTb detail, ExecutionStepTb step);
}
