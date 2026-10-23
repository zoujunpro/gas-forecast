package com.gas.forecast.business.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.core.ResponseResult;
import com.gas.forecast.business.enums.ExecutionStepCode;
import com.gas.forecast.business.service.ForecastApplicationService;
import com.gas.forecast.dao.domain.BaseCustomerTb;
import com.gas.forecast.dao.domain.ExecutionStepTb;
import com.gas.forecast.dao.domain.ExecutionTaskDetailTb;
import com.gas.forecast.dao.domain.ExecutionTaskTb;
import com.gas.forecast.dao.mapper.BaseCustomerTbMapper;
import com.gas.forecast.dao.mapper.ExecutionStepTbMapper;
import com.gas.forecast.dao.mapper.ExecutionTaskDetailTbMapper;
import com.gas.forecast.dao.mapper.ExecutionTaskTbMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import com.gas.forecast.business.service.ExecutionTaskEventService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 为批量训练、预测页面提供任务总览、任务明细及步骤查询。 */
@RestController
@RequestMapping("/execution-tasks")
@RequiredArgsConstructor
public class ExecutionTaskQueryController {
    private final ExecutionTaskTbMapper executionTaskTbMapper;
    private final ExecutionTaskDetailTbMapper executionTaskDetailTbMapper;
    private final ExecutionStepTbMapper executionStepTbMapper;
    private final BaseCustomerTbMapper baseCustomerTbMapper;
    private final ForecastApplicationService forecastApplicationService;
    private final ExecutionTaskEventService executionTaskEventService;

    /** 页面刷新后先读取步骤快照；任务未结束时再建立此事件流接收后续变化。 */
    @GetMapping(value = "/{taskNo}/events", produces = "text/event-stream")
    public SseEmitter events(@PathVariable String taskNo) {
        task(taskNo);
        return executionTaskEventService.subscribe(taskNo);
    }

    @GetMapping("/{taskNo}")
    public ResponseResult<Map<String, Object>> overview(@PathVariable String taskNo) {
        ExecutionTaskTb task = task(taskNo);
        List<ExecutionTaskDetailTb> details = executionTaskDetailTbMapper.selectList(new LambdaQueryWrapper<ExecutionTaskDetailTb>()
                .eq(ExecutionTaskDetailTb::getTaskNo, taskNo));
        Map<String, Long> counts = details.stream().collect(Collectors.groupingBy(item -> text(item.getStatus(), "PENDING"), Collectors.counting()));
        long completed = counts.getOrDefault("SUCCESS", 0L);
        long failed = counts.getOrDefault("FAILED", 0L);
        long cancelled = counts.getOrDefault("CANCELLED", 0L);
        long total = details.size();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskNo", task.getTaskNo()); result.put("taskName", task.getTaskName());
        result.put("businessType", task.getBusinessType()); result.put("dimensionType", task.getDimensionType()); result.put("status", task.getStatus());
        result.put("total", total); result.put("completed", completed); result.put("running", counts.getOrDefault("RUNNING", 0L) + counts.getOrDefault("TRAINING", 0L) + counts.getOrDefault("FORECASTING", 0L));
        result.put("pending", Math.max(0, total - completed - failed - cancelled - ((Number) result.get("running")).longValue()));
        result.put("failed", failed); result.put("cancelled", cancelled);
        result.put("progressPercent", total == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(completed + failed + cancelled).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP));
        return ResponseResult.success(result);
    }

    @GetMapping("/{taskNo}/details")
    public ResponseResult<Map<String, Object>> details(@PathVariable String taskNo, @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize, @RequestParam(required = false) String status) {
        task(taskNo);
        Page<ExecutionTaskDetailTb> page = executionTaskDetailTbMapper.selectPage(new Page<>(Math.max(pageNo, 1), Math.min(Math.max(pageSize, 1), 100)),
                new LambdaQueryWrapper<ExecutionTaskDetailTb>().eq(ExecutionTaskDetailTb::getTaskNo, taskNo)
                        .eq(status != null && !status.isBlank(), ExecutionTaskDetailTb::getStatus, status).orderByAsc(ExecutionTaskDetailTb::getDetailNo));
        Map<String, BaseCustomerTb> customers = customerMap(page.getRecords());
        Map<String, List<ExecutionStepTb>> stepsByDetail = stepsByDetail(page.getRecords());
        List<Map<String, Object>> records = page.getRecords().stream().map(detail -> detailView(detail, customers.get(detail.getCustomerCode()),
                stepsByDetail.getOrDefault(detail.getDetailNo(), List.of()))).toList();
        return ResponseResult.success(Map.of("records", records, "total", page.getTotal(), "pageNo", page.getCurrent(), "pageSize", page.getSize()));
    }

    @GetMapping("/details/{detailNo}/steps")
    public ResponseResult<Map<String, Object>> steps(@PathVariable String detailNo) {
        ExecutionTaskDetailTb detail = executionTaskDetailTbMapper.selectOne(new LambdaQueryWrapper<ExecutionTaskDetailTb>().eq(ExecutionTaskDetailTb::getDetailNo, detailNo).last("limit 1"));
        if (detail == null) throw new BusinessException("任务明细不存在：" + detailNo);
        List<ExecutionStepTb> steps = executionStepTbMapper.selectList(new LambdaQueryWrapper<ExecutionStepTb>().eq(ExecutionStepTb::getTaskDetailNo, detailNo)
                .orderByDesc(ExecutionStepTb::getAttemptNo).orderByAsc(ExecutionStepTb::getStepOrder));
        return ResponseResult.success(Map.of("detailNo", detailNo, "taskNo", detail.getTaskNo(), "status", detail.getStatus(), "steps", steps));
    }

    /**
     * 对话中的训练/预测结果卡片只使用这个聚合结果，不从执行步骤的原始日志或 JSON 推导。
     */
    @GetMapping("/details/{detailNo}/result")
    public ResponseResult<Map<String, Object>> result(@PathVariable String detailNo) {
        ExecutionTaskDetailTb detail = executionTaskDetailTbMapper.selectOne(new LambdaQueryWrapper<ExecutionTaskDetailTb>()
                .eq(ExecutionTaskDetailTb::getDetailNo, detailNo).last("limit 1"));
        if (detail == null) throw new BusinessException("任务明细不存在：" + detailNo);
        if (detail.getForecastBatchNo() == null || detail.getForecastBatchNo().isBlank())
            throw new BusinessException("该任务尚未生成预测结果");
        Map<String, Object> result = new LinkedHashMap<>();
        result.putAll((Map<String, Object>) forecastApplicationService.result(detail.getForecastBatchNo(), true));
        result.put("detailNo", detailNo);
        result.put("customer", customerMap(List.of(detail)).get(detail.getCustomerCode()));
        return ResponseResult.success(result);
    }

    private ExecutionTaskTb task(String taskNo) {
        ExecutionTaskTb task = executionTaskTbMapper.selectOne(new LambdaQueryWrapper<ExecutionTaskTb>().eq(ExecutionTaskTb::getTaskNo, taskNo).last("limit 1"));
        if (task == null) throw new BusinessException("执行任务不存在：" + taskNo);
        return task;
    }
    private Map<String, BaseCustomerTb> customerMap(List<ExecutionTaskDetailTb> details) {
        Set<String> codes = details.stream().map(ExecutionTaskDetailTb::getCustomerCode).filter(value -> value != null && !value.isBlank()).collect(Collectors.toSet());
        if (codes.isEmpty()) return Map.of();
        return baseCustomerTbMapper.selectList(new LambdaQueryWrapper<BaseCustomerTb>().in(BaseCustomerTb::getCustomerCode, codes)).stream()
                .collect(Collectors.toMap(BaseCustomerTb::getCustomerCode, Function.identity(), (left, right) -> left));
    }
    private Map<String, List<ExecutionStepTb>> stepsByDetail(List<ExecutionTaskDetailTb> details) {
        Set<String> detailNos = details.stream().map(ExecutionTaskDetailTb::getDetailNo).filter(value -> value != null && !value.isBlank()).collect(Collectors.toSet());
        if (detailNos.isEmpty()) return Map.of();
        return executionStepTbMapper.selectList(new LambdaQueryWrapper<ExecutionStepTb>().in(ExecutionStepTb::getTaskDetailNo, detailNos)
                        .orderByAsc(ExecutionStepTb::getStepOrder))
                .stream().collect(Collectors.groupingBy(ExecutionStepTb::getTaskDetailNo));
    }
    private Map<String, Object> detailView(ExecutionTaskDetailTb detail, BaseCustomerTb customer, List<ExecutionStepTb> steps) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("detailNo", detail.getDetailNo()); result.put("dimensionType", detail.getDimensionType());
        result.put("status", detail.getStatus()); result.put("regionCode", detail.getRegionCode()); result.put("industryCode", detail.getIndustryCode()); result.put("customerCode", detail.getCustomerCode());
        result.put("targetName", customer == null ? targetName(detail) : customer.getCustomerName());
        result.put("regionName", customer == null ? null : customer.getRegionName()); result.put("industryName", customer == null ? null : customer.getIndustryName());
        result.put("trainBatchNo", detail.getTrainBatchNo()); result.put("forecastBatchNo", detail.getForecastBatchNo()); result.put("createdAt", detail.getCreatedAt()); result.put("updatedAt", detail.getUpdatedAt());
        result.put("steps", stepSummaries(steps));
        return result;
    }
    private List<Map<String, Object>> stepSummaries(List<ExecutionStepTb> steps) {
        Map<String, ExecutionStepTb> byCode = steps.stream().collect(Collectors.toMap(ExecutionStepTb::getStepCode, Function.identity(), (left, right) -> left));
        return java.util.Arrays.stream(ExecutionStepCode.values()).map(code -> {
            ExecutionStepTb step = byCode.get(code.name());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("stepNo", step == null ? null : step.getStepNo()); result.put("stepCode", code.name()); result.put("stepName", code.getName()); result.put("stepOrder", code.getOrder());
            result.put("status", step == null ? "PENDING" : step.getStatus()); result.put("message", step == null ? null : step.getMessage()); result.put("errorMessage", step == null ? null : step.getErrorMessage());
            result.put("durationMs", step == null ? null : step.getDurationMs());
            // 前端展示节奏以真实步骤结束时间为下限：慢任务不等待，快任务才补足最短展示时间。
            result.put("createdAt", step == null ? null : step.getCreatedAt());
            result.put("updatedAt", step == null ? null : step.getUpdatedAt());
            return result;
        }).toList();
    }
    private String targetName(ExecutionTaskDetailTb detail) { return "REGION".equals(detail.getDimensionType()) ? text(detail.getRegionCode(), "地区任务") : "INDUSTRY".equals(detail.getDimensionType()) ? text(detail.getIndustryCode(), "行业任务") : text(detail.getCustomerCode(), "客户任务"); }
    private String text(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
}
