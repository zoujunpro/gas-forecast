package com.gas.forecast.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gas.forecast.business.application.forecast.ForecastLastYearComparisonService;
import com.gas.forecast.business.application.task.event.ExecutionTaskEventService;
import com.gas.forecast.business.enums.ExecutionStepCodeEnum;
import com.gas.forecast.business.service.ExecutionTaskQueryService;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.dao.domain.BaseCustomerTb;
import com.gas.forecast.dao.domain.ExecutionStepTb;
import com.gas.forecast.dao.domain.ExecutionTaskDetailTb;
import com.gas.forecast.dao.domain.ExecutionTaskTb;
import com.gas.forecast.dao.domain.ModelExecuteRecordTb;
import com.gas.forecast.dao.mapper.BaseCustomerTbMapper;
import com.gas.forecast.dao.mapper.ExecutionStepTbMapper;
import com.gas.forecast.dao.mapper.ExecutionTaskDetailTbMapper;
import com.gas.forecast.dao.mapper.ExecutionTaskTbMapper;
import com.gas.forecast.dao.mapper.ModelExecuteRecordTbMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 任务查询、步骤快照、结果聚合的应用实现。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExecutionTaskQueryServiceImpl implements ExecutionTaskQueryService {
    private final ExecutionTaskTbMapper taskMapper;
    private final ExecutionTaskDetailTbMapper detailMapper;
    private final ExecutionStepTbMapper stepMapper;
    private final BaseCustomerTbMapper customerMapper;
    private final ModelExecuteRecordTbMapper recordMapper;
    private final ExecutionTaskEventService eventService;
    private final ForecastLastYearComparisonService forecastLastYearComparisonService;

    @Override
    public SseEmitter subscribe(String taskNo) { task(taskNo); return eventService.subscribe(taskNo); }

    @Override
    public Map<String, Object> overview(String taskNo) {
        ExecutionTaskTb task = task(taskNo);
        List<ExecutionTaskDetailTb> details = detailMapper.selectList(new LambdaQueryWrapper<ExecutionTaskDetailTb>().eq(ExecutionTaskDetailTb::getTaskNo, taskNo));
        Map<String, Long> counts = details.stream().collect(Collectors.groupingBy(item -> text(item.getStatus(), "PENDING"), Collectors.counting()));
        long completed = counts.getOrDefault("SUCCESS", 0L), failed = counts.getOrDefault("FAILED", 0L), cancelled = counts.getOrDefault("CANCELLED", 0L), total = details.size();
        long running = counts.getOrDefault("RUNNING", 0L) + counts.getOrDefault("TRAINING", 0L) + counts.getOrDefault("FORECASTING", 0L);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskNo", task.getTaskNo()); result.put("taskName", task.getTaskName());
        result.put("dimensionType", task.getDimensionType()); result.put("status", task.getStatus()); result.put("total", total); result.put("completed", completed);
        result.put("running", running); result.put("pending", Math.max(0, total - completed - failed - cancelled - running)); result.put("failed", failed); result.put("cancelled", cancelled);
        result.put("progressPercent", total == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(completed + failed + cancelled).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP));
        return result;
    }

    @Override
    public Map<String, Object> details(String taskNo, long pageNo, long pageSize, String status) {
        task(taskNo);
        Page<ExecutionTaskDetailTb> page = detailMapper.selectPage(new Page<>(Math.max(pageNo, 1), Math.min(Math.max(pageSize, 1), 100)),
                new LambdaQueryWrapper<ExecutionTaskDetailTb>().eq(ExecutionTaskDetailTb::getTaskNo, taskNo)
                        .eq(status != null && !status.isBlank(), ExecutionTaskDetailTb::getStatus, status).orderByAsc(ExecutionTaskDetailTb::getDetailNo));
        Map<String, BaseCustomerTb> customers = customerMap(page.getRecords());
        Map<String, List<ExecutionStepTb>> steps = stepsByDetail(page.getRecords());
        List<Map<String, Object>> records = page.getRecords().stream().map(detail -> detailView(detail, customers.get(detail.getCustomerCode()), steps.getOrDefault(detail.getDetailNo(), List.of()))).toList();
        return Map.of("records", records, "total", page.getTotal(), "pageNo", page.getCurrent(), "pageSize", page.getSize());
    }

    @Override
    public Map<String, Object> steps(String detailNo) {
        ExecutionTaskDetailTb detail = detailMapper.selectOne(new LambdaQueryWrapper<ExecutionTaskDetailTb>().eq(ExecutionTaskDetailTb::getDetailNo, detailNo).last("limit 1"));
        if (detail == null) throw new BusinessException("任务明细不存在：" + detailNo);
        List<ExecutionStepTb> steps = stepMapper.selectList(new LambdaQueryWrapper<ExecutionStepTb>().eq(ExecutionStepTb::getTaskDetailNo, detailNo).orderByDesc(ExecutionStepTb::getAttemptNo).orderByAsc(ExecutionStepTb::getStepOrder));
        return Map.of("detailNo", detailNo, "taskNo", detail.getTaskNo(), "status", detail.getStatus(), "steps", steps);
    }

    @Override
    public Map<String, Object> result(String detailNo) {
        ExecutionTaskDetailTb detail = detailMapper.selectOne(new LambdaQueryWrapper<ExecutionTaskDetailTb>().eq(ExecutionTaskDetailTb::getDetailNo, detailNo).last("limit 1"));
        if (detail == null) throw new BusinessException("任务明细不存在：" + detailNo);
        if (detail.getForecastNo() == null || detail.getForecastNo().isBlank()) throw new BusinessException("该任务尚未生成预测结果");
        ModelExecuteRecordTb record = recordMapper.selectOne(new LambdaQueryWrapper<ModelExecuteRecordTb>().eq(ModelExecuteRecordTb::getForecastNo, detail.getForecastNo()).last("limit 1"));
        if (record == null) throw new BusinessException("该任务尚未生成预测结果");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("record", record); result.put("training", record.getTrainingResultJson()); result.put("backtest", record.getBacktestResultJson()); result.put("forecast", record.getForecastResultJson());
        result.put("lastYearPoints", forecastLastYearComparisonService.lastYearPoints(record));
        result.put("detailNo", detailNo); result.put("customer", customerMap(List.of(detail)).get(detail.getCustomerCode()));
        return result;
    }

    private ExecutionTaskTb task(String taskNo) {
        ExecutionTaskTb task = taskMapper.selectOne(new LambdaQueryWrapper<ExecutionTaskTb>().eq(ExecutionTaskTb::getTaskNo, taskNo).last("limit 1"));
        if (task == null) throw new BusinessException("执行任务不存在：" + taskNo);
        return task;
    }
    private Map<String, BaseCustomerTb> customerMap(List<ExecutionTaskDetailTb> details) {
        Set<String> codes = details.stream().map(ExecutionTaskDetailTb::getCustomerCode).filter(value -> value != null && !value.isBlank()).collect(Collectors.toSet());
        if (codes.isEmpty()) return Map.of();
        return customerMapper.selectList(new LambdaQueryWrapper<BaseCustomerTb>().in(BaseCustomerTb::getCustomerCode, codes)).stream().collect(Collectors.toMap(BaseCustomerTb::getCustomerCode, Function.identity(), (left, right) -> left));
    }
    private Map<String, List<ExecutionStepTb>> stepsByDetail(List<ExecutionTaskDetailTb> details) {
        Set<String> detailNos = details.stream().map(ExecutionTaskDetailTb::getDetailNo).filter(value -> value != null && !value.isBlank()).collect(Collectors.toSet());
        if (detailNos.isEmpty()) return Map.of();
        return stepMapper.selectList(new LambdaQueryWrapper<ExecutionStepTb>().in(ExecutionStepTb::getTaskDetailNo, detailNos).orderByAsc(ExecutionStepTb::getStepOrder)).stream().collect(Collectors.groupingBy(ExecutionStepTb::getTaskDetailNo));
    }
    private Map<String, Object> detailView(ExecutionTaskDetailTb detail, BaseCustomerTb customer, List<ExecutionStepTb> steps) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("detailNo", detail.getDetailNo()); result.put("dimensionType", detail.getDimensionType()); result.put("status", detail.getStatus());
        result.put("regionCode", detail.getRegionCode()); result.put("industryCode", detail.getIndustryCode()); result.put("customerCode", detail.getCustomerCode());
        result.put("targetName", customer == null ? targetName(detail) : customer.getCustomerName()); result.put("regionName", customer == null ? null : customer.getRegionName()); result.put("industryName", customer == null ? null : customer.getIndustryName());
        result.put("forecastNo", detail.getForecastNo()); result.put("createdAt", detail.getCreatedAt()); result.put("updatedAt", detail.getUpdatedAt()); result.put("steps", stepSummaries(steps));
        return result;
    }
    private List<Map<String, Object>> stepSummaries(List<ExecutionStepTb> steps) {
        Map<String, ExecutionStepTb> byCode = steps.stream().collect(Collectors.toMap(ExecutionStepTb::getStepCode, Function.identity(), (left, right) -> left));
        return java.util.Arrays.stream(ExecutionStepCodeEnum.values()).map(code -> {
            ExecutionStepTb step = byCode.get(code.name()); Map<String, Object> result = new LinkedHashMap<>();
            result.put("stepNo", step == null ? null : step.getStepNo()); result.put("stepCode", code.name()); result.put("stepName", code.getName()); result.put("stepOrder", code.getOrder());
            result.put("status", step == null ? "PENDING" : step.getStatus()); result.put("message", step == null ? null : step.getMessage()); result.put("errorMessage", step == null ? null : step.getErrorMessage()); result.put("durationMs", step == null ? null : step.getDurationMs());
            result.put("createdAt", step == null ? null : step.getCreatedAt()); result.put("updatedAt", step == null ? null : step.getUpdatedAt()); return result;
        }).toList();
    }
    private String targetName(ExecutionTaskDetailTb detail) { return "REGION".equals(detail.getDimensionType()) ? text(detail.getRegionCode(), "地区任务") : "INDUSTRY".equals(detail.getDimensionType()) ? text(detail.getIndustryCode(), "行业任务") : text(detail.getCustomerCode(), "客户任务"); }
    private String text(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
}
