package com.gas.forecast.business.common.scheduler;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.dao.domain.AgentRunEventTb;
import com.gas.forecast.dao.mapper.AgentRunEventTbMapper;
import java.util.Date;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 处理因服务异常退出而没有收到最终回调的运行事件。
 *
 * <p>
 * 长模型步骤会周期性刷新 {@code updated_at}；超过阈值仍为 RUNNING 才判定中断， 不会把正常但暂时无前端刷新的任务误判为失败。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "gas.agent.run-event-recovery", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AgentRunEventRecoveryScheduler {
    private final AgentRunEventTbMapper eventMapper;
    @Value("${gas.agent.run-event-recovery.timeout-ms:300000}")
    private long timeoutMillis;

    @Scheduled(fixedDelayString = "${gas.agent.run-event-recovery.scan-delay-ms:60000}")
    public void interruptStaleRunningEvents() {
        Date cutoff = new Date(System.currentTimeMillis() - timeoutMillis);
        List<AgentRunEventTb> staleEvents = eventMapper.selectList(Wrappers.<AgentRunEventTb>lambdaQuery().eq(AgentRunEventTb::getStatus, "RUNNING").lt(AgentRunEventTb::getUpdatedAt, cutoff).orderByAsc(AgentRunEventTb::getId).last("limit 100"));
        for (AgentRunEventTb event : staleEvents) {
            if (hasRecentRunningSibling(event, cutoff))
                continue;
            event.setStatus("INTERRUPTED");
            event.setMessage("执行服务中断，未收到后续进度更新。");
            event.setErrorCode("EXECUTION_HEARTBEAT_TIMEOUT");
            event.setErrorMessage("运行事件超过5分钟未更新，已标记为中断。");
            event.setUpdatedAt(new Date());
            eventMapper.updateById(event);
            log.warn("event=agent_run_event_interrupted event_id={} run_no={}", event.getId(), event.getRunNo());
        }
    }

    /** 同一预测对象仍有模型训练/回测心跳时，外层等待模型响应事件不能被误判为中断。 */
    private boolean hasRecentRunningSibling(AgentRunEventTb event, Date cutoff) {
        if (event.getTaskDetailNo() == null || event.getTaskDetailNo().isBlank())
            return false;
        Long activeCount = eventMapper.selectCount(Wrappers.<AgentRunEventTb>lambdaQuery().eq(AgentRunEventTb::getRunNo, event.getRunNo()).eq(AgentRunEventTb::getTaskDetailNo, event.getTaskDetailNo()).eq(AgentRunEventTb::getStatus, "RUNNING").ge(AgentRunEventTb::getUpdatedAt, cutoff).ne(AgentRunEventTb::getId, event.getId()));
        return activeCount != null && activeCount > 0;
    }
}
