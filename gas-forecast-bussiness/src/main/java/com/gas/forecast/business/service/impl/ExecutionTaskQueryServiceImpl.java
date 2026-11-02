package com.gas.forecast.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gas.forecast.business.application.forecast.ForecastLastYearComparisonService;
import com.gas.forecast.business.service.ExecutionTaskQueryService;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.AgentRunEventTb;
import com.gas.forecast.dao.domain.BaseCustomerTb;
import com.gas.forecast.dao.domain.ExecutionTaskDetailTb;
import com.gas.forecast.dao.domain.ExecutionTaskTb;
import com.gas.forecast.dao.domain.ModelExecuteRecordTb;
import com.gas.forecast.dao.mapper.AgentRunEventTbMapper;
import com.gas.forecast.dao.mapper.BaseCustomerTbMapper;
import com.gas.forecast.dao.mapper.ExecutionTaskDetailTbMapper;
import com.gas.forecast.dao.mapper.ExecutionTaskTbMapper;
import com.gas.forecast.dao.mapper.ModelExecuteRecordTbMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 任务查询仅返回任务、预测结果和智能体运行事件，不再依赖固定步骤表。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExecutionTaskQueryServiceImpl implements ExecutionTaskQueryService {
    private final ExecutionTaskTbMapper taskMapper;
    private final ExecutionTaskDetailTbMapper detailMapper;
    private final AgentRunEventTbMapper eventMapper;
    private final BaseCustomerTbMapper customerMapper;
    private final ModelExecuteRecordTbMapper recordMapper;
    private final ForecastLastYearComparisonService forecastLastYearComparisonService;

    @Override public Map<String, Object> overview(String taskNo) {
        ExecutionTaskTb task = task(taskNo);
        List<ExecutionTaskDetailTb> details = detailMapper.selectList(new LambdaQueryWrapper<ExecutionTaskDetailTb>().eq(ExecutionTaskDetailTb::getTaskNo, taskNo));
        Map<String, Long> counts = details.stream().collect(Collectors.groupingBy(item -> text(item.getStatus(), "PENDING"), Collectors.counting()));
        long completed = counts.getOrDefault("SUCCESS", 0L), failed = counts.getOrDefault("FAILED", 0L), cancelled = counts.getOrDefault("CANCELLED", 0L), total = details.size();
        long running = counts.getOrDefault("RUNNING", 0L) + counts.getOrDefault("TRAINING", 0L) + counts.getOrDefault("FORECASTING", 0L);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskNo", task.getTaskNo()); result.put("runNo", task.getRunNo()); result.put("taskName", task.getTaskName()); result.put("dimensionType", task.getDimensionType()); result.put("status", task.getStatus());
        result.put("total", total); result.put("completed", completed); result.put("running", running); result.put("pending", Math.max(0, total - completed - failed - cancelled - running)); result.put("failed", failed); result.put("cancelled", cancelled);
        result.put("progressPercent", total == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(completed + failed + cancelled).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP));
        return result;
    }

    @Override public Map<String, Object> details(String taskNo, long pageNo, long pageSize, String status) {
        ExecutionTaskTb task = task(taskNo);
        Page<ExecutionTaskDetailTb> page = detailMapper.selectPage(new Page<>(Math.max(pageNo, 1), Math.min(Math.max(pageSize, 1), 100)), new LambdaQueryWrapper<ExecutionTaskDetailTb>().eq(ExecutionTaskDetailTb::getTaskNo, taskNo).eq(status != null && !status.isBlank(), ExecutionTaskDetailTb::getStatus, status).orderByAsc(ExecutionTaskDetailTb::getDetailNo));
        Map<String, BaseCustomerTb> customers = customerMap(page.getRecords());
        Map<String, List<AgentRunEventTb>> events = detailEvents(task, page.getRecords());
        return Map.of("records", page.getRecords().stream().map(detail -> detailView(detail, customers.get(detail.getCustomerCode()), events.getOrDefault(detail.getDetailNo(), List.of()))).toList(), "total", page.getTotal(), "pageNo", page.getCurrent(), "pageSize", page.getSize());
    }

    @Override public Map<String, Object> events(String detailNo) {
        ExecutionTaskDetailTb detail = detail(detailNo); ExecutionTaskTb task = task(detail.getTaskNo());
        List<AgentRunEventTb> events = detailEvents(task, List.of(detail)).getOrDefault(detailNo, List.of());
        return Map.of("detailNo", detailNo, "taskNo", detail.getTaskNo(), "status", detail.getStatus(), "events", events, "process", processItems(events));
    }

    @Override public Map<String, Object> result(String detailNo) {
        ExecutionTaskDetailTb detail = detail(detailNo);
        if (detail.getForecastNo() == null || detail.getForecastNo().isBlank()) throw new BusinessException("该任务尚未生成预测结果");
        ModelExecuteRecordTb record = recordMapper.selectOne(new LambdaQueryWrapper<ModelExecuteRecordTb>().eq(ModelExecuteRecordTb::getForecastNo, detail.getForecastNo()).last("limit 1"));
        if (record == null) throw new BusinessException("该任务尚未生成预测结果");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("record", record); result.put("training", record.getTrainingResultJson()); result.put("backtest", record.getBacktestResultJson()); result.put("forecast", record.getForecastResultJson()); result.put("lastYearPoints", forecastLastYearComparisonService.lastYearPoints(record)); result.put("detailNo", detailNo); result.put("customer", customerMap(List.of(detail)).get(detail.getCustomerCode()));
        return result;
    }
    private ExecutionTaskTb task(String taskNo) { ExecutionTaskTb item = taskMapper.selectOne(new LambdaQueryWrapper<ExecutionTaskTb>().eq(ExecutionTaskTb::getTaskNo, taskNo).last("limit 1")); if (item == null) throw new BusinessException("执行任务不存在：" + taskNo); return item; }
    private ExecutionTaskDetailTb detail(String detailNo) { ExecutionTaskDetailTb item = detailMapper.selectOne(new LambdaQueryWrapper<ExecutionTaskDetailTb>().eq(ExecutionTaskDetailTb::getDetailNo, detailNo).last("limit 1")); if (item == null) throw new BusinessException("任务明细不存在：" + detailNo); return item; }
    private Map<String, BaseCustomerTb> customerMap(List<ExecutionTaskDetailTb> details) { Set<String> codes = details.stream().map(ExecutionTaskDetailTb::getCustomerCode).filter(value -> value != null && !value.isBlank()).collect(Collectors.toSet()); return codes.isEmpty() ? Map.of() : customerMapper.selectList(new LambdaQueryWrapper<BaseCustomerTb>().in(BaseCustomerTb::getCustomerCode, codes)).stream().collect(Collectors.toMap(BaseCustomerTb::getCustomerCode, item -> item, (left, right) -> left)); }
    private Map<String, List<AgentRunEventTb>> detailEvents(ExecutionTaskTb task, List<ExecutionTaskDetailTb> details) {
        if (!TextUtils.hasText(task.getRunNo()) || details.isEmpty()) return Map.of();
        Set<String> detailNos = details.stream().map(ExecutionTaskDetailTb::getDetailNo).collect(Collectors.toSet());
        List<AgentRunEventTb> all = eventMapper.selectList(new LambdaQueryWrapper<AgentRunEventTb>()
                .eq(AgentRunEventTb::getRunNo, task.getRunNo()).orderByAsc(AgentRunEventTb::getId));
        Map<String, List<AgentRunEventTb>> grouped = all.stream()
                .filter(event -> TextUtils.hasText(event.getTaskDetailNo()) && detailNos.contains(event.getTaskDetailNo()))
                .collect(Collectors.groupingBy(AgentRunEventTb::getTaskDetailNo, LinkedHashMap::new, Collectors.toList()));
        // 单任务没有歧义时，把智能体理解、工具受理等没有明细编号的事件也放到该任务下，
        // 让用户看到从理解请求到模型结果的完整时间线。
        if (details.size() == 1) {
            List<AgentRunEventTb> target = new java.util.ArrayList<>(grouped.getOrDefault(details.get(0).getDetailNo(), List.of()));
            target.addAll(all.stream().filter(event -> !TextUtils.hasText(event.getTaskDetailNo())).toList());
            // 运行级事件与明细事件可能交错产生，合并后仍必须按事件主键还原真实时序。
            target.sort(java.util.Comparator.comparing(AgentRunEventTb::getId,
                    java.util.Comparator.nullsLast(Long::compareTo)));
            grouped.put(details.get(0).getDetailNo(), target);
        }
        return grouped;
    }

    private List<Map<String, Object>> processItems(List<AgentRunEventTb> events) {
        return java.util.stream.IntStream.range(0, events.size()).mapToObj(index -> {
            AgentRunEventTb event = events.get(index);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("eventId", event.getId());
            item.put("eventCode", event.getEventCode());
            item.put("eventName", eventName(event.getEventCode()));
            // 前端过程卡的渲染字段；数据本身仍是 agent_run_event_tb 的真实事件，
            // 不代表也不读取旧 execution_step_tb。
            item.put("stepNo", event.getId());
            item.put("stepCode", event.getEventCode());
            item.put("stepName", eventName(event.getEventCode()));
            item.put("message", event.getMessage());
            item.put("eventDataJson", event.getEventDataJson());
            item.put("metricsJson", event.getEventDataJson());
            item.put("status", event.getStatus());
            item.put("errorCode", event.getErrorCode());
            item.put("errorMessage", event.getErrorMessage());
            item.put("createdAt", event.getCreatedAt());
            item.put("updatedAt", event.getUpdatedAt());
            item.put("order", index + 1);
            return item;
        }).toList();
    }

    private String eventName(String code) {
        return switch (text(code, "")) {
            case "AGENT_ACTION" -> "理解请求";
            case "TOOL_CALL" -> "调用业务能力";
            case "DATA_QUERY" -> "查询业务数据";
            case "FEATURE_PREPARE" -> "准备训练特征";
            case "DYNAMIC_INDICATOR" -> "准备动态特征";
            case "FORECAST", "MODEL_PREDICT" -> "生成预测结果";
            case "RESULT_ANALYZE" -> "分析预测结果";
            case "MODEL_VALIDATE" -> "校验模型数据";
            case "MODEL_TRAIN" -> "训练候选模型";
            case "MODEL_BACKTEST" -> "执行模型回测";
            case "MODEL_SELECT" -> "选择最优模型";
            case "MODEL_ARTIFACT" -> "保存模型产物";
            default -> code;
        };
    }

    private Map<String, Object> detailView(ExecutionTaskDetailTb detail, BaseCustomerTb customer, List<AgentRunEventTb> events) { Map<String, Object> result = new LinkedHashMap<>(); result.put("detailNo", detail.getDetailNo()); result.put("dimensionType", detail.getDimensionType()); result.put("status", detail.getStatus()); result.put("regionCode", detail.getRegionCode()); result.put("industryCode", detail.getIndustryCode()); result.put("customerCode", detail.getCustomerCode()); result.put("targetName", customer == null ? targetName(detail) : customer.getCustomerName()); result.put("regionName", customer == null ? null : customer.getRegionName()); result.put("industryName", customer == null ? null : customer.getIndustryName()); result.put("forecastNo", detail.getForecastNo()); result.put("createdAt", detail.getCreatedAt()); result.put("updatedAt", detail.getUpdatedAt()); result.put("events", events); result.put("process", processItems(events)); return result; }
    private String targetName(ExecutionTaskDetailTb detail) { return "REGION".equals(detail.getDimensionType()) ? text(detail.getRegionCode(), "地区任务") : "INDUSTRY".equals(detail.getDimensionType()) ? text(detail.getIndustryCode(), "行业任务") : text(detail.getCustomerCode(), "客户任务"); }
    private String text(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
}
