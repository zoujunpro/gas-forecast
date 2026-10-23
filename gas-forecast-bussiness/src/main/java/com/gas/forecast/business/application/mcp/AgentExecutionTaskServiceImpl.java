package com.gas.forecast.business.application.mcp;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.business.application.mcp.AgentExecutionTaskService;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.security.context.SecurityContextHolder;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.ExecutionStepTb;
import com.gas.forecast.dao.domain.ExecutionTaskDetailTb;
import com.gas.forecast.dao.domain.ExecutionTaskTb;
import com.gas.forecast.dao.mapper.ExecutionStepTbMapper;
import com.gas.forecast.dao.mapper.ExecutionTaskDetailTbMapper;
import com.gas.forecast.dao.mapper.ExecutionTaskTbMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 新执行模型查询：任务、任务明细和步骤是唯一状态来源。 */
@Service
@RequiredArgsConstructor
public class AgentExecutionTaskServiceImpl implements AgentExecutionTaskService {
    private final ExecutionTaskTbMapper executionTaskTbMapper;
    private final ExecutionTaskDetailTbMapper executionTaskDetailTbMapper;
    private final ExecutionStepTbMapper executionStepTbMapper;

    @Override
    public Object status(String taskNo, boolean includeRecovery) {
        ExecutionTaskTb task = owned(taskNo);
        List<ExecutionTaskDetailTb> details = executionTaskDetailTbMapper.selectList(Wrappers.<ExecutionTaskDetailTb>lambdaQuery()
                .eq(ExecutionTaskDetailTb::getTaskNo, taskNo).orderByAsc(ExecutionTaskDetailTb::getDetailNo));
        Map<String, Long> summary = new LinkedHashMap<>();
        summary.put("total", (long) details.size());
        summary.put("pending", details.stream().filter(item -> "PENDING".equals(item.getStatus()) || "WAITING_FORECAST".equals(item.getStatus())).count());
        summary.put("running", details.stream().filter(item -> "TRAINING".equals(item.getStatus()) || "FORECASTING".equals(item.getStatus()) || "RUNNING".equals(item.getStatus())).count());
        summary.put("success", details.stream().filter(item -> "SUCCESS".equals(item.getStatus())).count());
        summary.put("failed", details.stream().filter(item -> "FAILED".equals(item.getStatus())).count());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskNo", task.getTaskNo());
        result.put("dimensionType", task.getDimensionType()); result.put("status", task.getStatus());
        result.put("currentStepCode", task.getCurrentStepCode()); result.put("errorCode", task.getErrorCode());
        result.put("errorMessage", task.getErrorMessage()); result.put("summary", summary);
        result.put("details", details); result.put("nextAction", terminal(task.getStatus()) ? "NONE" : "WAIT");
        return result;
    }

    @Override
    public Object steps(String taskNo, boolean includeBusinessSteps, boolean includeAttempts) {
        owned(taskNo);
        List<String> detailNos = executionTaskDetailTbMapper.selectList(Wrappers.<ExecutionTaskDetailTb>lambdaQuery()
                .select(ExecutionTaskDetailTb::getDetailNo).eq(ExecutionTaskDetailTb::getTaskNo, taskNo))
                .stream().map(ExecutionTaskDetailTb::getDetailNo).toList();
        List<ExecutionStepTb> steps = detailNos.isEmpty() ? List.of() : executionStepTbMapper.selectList(Wrappers.<ExecutionStepTb>lambdaQuery()
                .in(ExecutionStepTb::getTaskDetailNo, detailNos).orderByAsc(ExecutionStepTb::getStepOrder));
        return Map.of("taskNo", taskNo, "steps", steps);
    }

    private ExecutionTaskTb owned(String taskNo) {
        if (!TextUtils.hasText(taskNo)) throw new BusinessException("taskNo不能为空");
        ExecutionTaskTb task = executionTaskTbMapper.selectOne(Wrappers.<ExecutionTaskTb>lambdaQuery()
                .eq(ExecutionTaskTb::getTaskNo, taskNo).eq(ExecutionTaskTb::getCreatedBy, String.valueOf(SecurityContextHolder.getUserId())).last("limit 1"));
        if (task == null) throw new BusinessException("任务不存在或无权访问");
        return task;
    }

    private boolean terminal(String status) { return "COMPLETED".equals(status) || "FAILED".equals(status) || "CANCELLED".equals(status); }
}
