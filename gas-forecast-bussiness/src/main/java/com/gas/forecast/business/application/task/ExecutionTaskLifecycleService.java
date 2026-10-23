package com.gas.forecast.business.application.task;

import com.gas.forecast.business.application.task.step.ExecutionStepRecorder;
import com.gas.forecast.business.common.support.BaseCodeGenerateService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.business.dto.request.ExecutionTarget;
import com.gas.forecast.business.enums.ExecutionTargetStatusEnum;
import com.gas.forecast.business.enums.BaseCodeTypeEnum;
import com.gas.forecast.business.enums.ExecutionDimensionTypeEnum;
import com.gas.forecast.business.enums.ExecutionTaskStatusEnum;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.security.context.SecurityContextHolder;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.ExecutionTaskDetailTb;
import com.gas.forecast.dao.domain.ExecutionTaskTb;
import com.gas.forecast.dao.domain.BaseCustomerTb;
import com.gas.forecast.dao.mapper.BaseCustomerTbMapper;
import com.gas.forecast.dao.mapper.ExecutionTaskDetailTbMapper;
import com.gas.forecast.dao.mapper.ExecutionTaskTbMapper;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 统一维护用户任务及其最小执行目标关联，不再创建执行批次汇总表。 */
@Service
@RequiredArgsConstructor
public class ExecutionTaskLifecycleService {
    private final ExecutionTaskTbMapper executionTaskTbMapper;
    private final ExecutionTaskDetailTbMapper executionTaskDetailTbMapper;
    private final BaseCustomerTbMapper baseCustomerTbMapper;
    private final BaseCodeGenerateService baseCodeGenerateService;
    private final ExecutionStepRecorder executionStepRecorder;
    private final ObjectMapper objectMapper;

    /** 创建一次用户原始意图对应的任务及最小目标关联行。 */
    @Transactional
    public ExecutionTaskTb create(String conversationNo, String agentCode,
            String dimensionType, List<ExecutionTarget> targets, String taskName) {
        return create(conversationNo, agentCode, dimensionType, targets, taskName, null);
    }

    /** 创建任务时持久化业务命令快照，供异步执行器恢复完整预测参数。 */
    @Transactional
    public ExecutionTaskTb create(String conversationNo, String agentCode,
            String dimensionType, List<ExecutionTarget> targets, String taskName, String requestParam) {
        Date now = new Date();
        ExecutionTaskTb task = new ExecutionTaskTb();
        task.setTaskNo(newTaskNo());
        task.setConversationNo(conversationNo);
        task.setDimensionType(dimensionType);
        task.setTaskName(taskName);
        task.setScopeConditionJson(json(targets));
        task.setRequestParam(requestParam);
        task.setStatus(ExecutionTaskStatusEnum.PENDING.getCode());
        task.setCreatedBy(String.valueOf(SecurityContextHolder.getUserId()));
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        executionTaskTbMapper.insert(task);

        List<ExecutionTaskDetailTb> relations = expand(task.getTaskNo(), agentCode, dimensionType, targets, now);
        if (relations.isEmpty()) throw new BusinessException("当前范围内没有可执行目标");
        for (int index = 0; index < relations.size(); index++) {
            ExecutionTaskDetailTb detail = relations.get(index);
            detail.setDetailNo(task.getTaskNo() + "-" + String.format("%06d", index + 1));
        }
        for (ExecutionTaskDetailTb relation : relations) {
            executionTaskDetailTbMapper.insert(relation);
            executionStepRecorder.initialize(relation);
        }
        return task;
    }

    /**
     * 仅展开并校验用户选择的范围，用于确认提示。
     *
     * <p>这里绝不能创建任务、任务明细或执行步骤。确认前产生任务会导致用户点击
     * “确认”后再次调用创建接口时出现两个 taskNo。</p>
     */
    @Transactional(readOnly = true)
    public int previewTargetCount(String agentCode, String dimensionType, List<ExecutionTarget> targets) {
        return expand("", agentCode, dimensionType, targets, new Date()).size();
    }

    /**
     * 原子领取统一预测任务明细。首次执行和失败重试都只能由一个 Worker 成功领取。
     */
    @Transactional
    public ExecutionTaskDetailTb claimForecastDetail(Long detailId) {
        int affected = executionTaskDetailTbMapper.update(null, Wrappers.<ExecutionTaskDetailTb>lambdaUpdate()
                .eq(ExecutionTaskDetailTb::getId, detailId)
                .in(ExecutionTaskDetailTb::getStatus, ExecutionTargetStatusEnum.PENDING.getCode(), ExecutionTargetStatusEnum.FAILED.getCode())
                .set(ExecutionTaskDetailTb::getStatus, ExecutionTargetStatusEnum.RUNNING.getCode())
                .set(ExecutionTaskDetailTb::getUpdatedAt, new Date()));
        if (affected != 1) return null;
        ExecutionTaskDetailTb detail = executionTaskDetailTbMapper.selectById(detailId);
        refreshTaskStatus(detail.getTaskNo());
        return detail;
    }

    /** 首次执行绑定稳定 forecastNo；重试时已有编号不允许被替换。 */
    @Transactional
    public void bindForecastNo(Long detailId, String forecastNo) {
        ExecutionTaskDetailTb detail = executionTaskDetailTbMapper.selectById(detailId);
        if (detail == null) throw new BusinessException("任务明细不存在");
        if (TextUtils.hasText(detail.getForecastNo()) && !detail.getForecastNo().equals(forecastNo))
            throw new BusinessException("任务明细已绑定预测编号，不能替换");
        if (!TextUtils.hasText(detail.getForecastNo())) {
            detail.setForecastNo(forecastNo);
            detail.setUpdatedAt(new Date());
            executionTaskDetailTbMapper.updateById(detail);
            executionStepRecorder.bindForecastNo(detail.getDetailNo(), forecastNo);
        }
    }

    /** 统一预测执行完成后回写任务明细，并聚合父任务状态。 */
    @Transactional
    public void completeForecastDetail(Long detailId, boolean success) {
        ExecutionTaskDetailTb detail = executionTaskDetailTbMapper.selectById(detailId);
        if (detail == null) throw new BusinessException("任务明细不存在");
        detail.setStatus(success ? ExecutionTargetStatusEnum.SUCCESS.getCode() : ExecutionTargetStatusEnum.FAILED.getCode());
        detail.setUpdatedAt(new Date());
        executionTaskDetailTbMapper.updateById(detail);
        refreshTaskStatus(detail.getTaskNo());
    }

    public List<ExecutionTaskDetailTb> relations(String taskNo) {
        return executionTaskDetailTbMapper.selectList(Wrappers.<ExecutionTaskDetailTb>lambdaQuery()
                .eq(ExecutionTaskDetailTb::getTaskNo, taskNo)
                .orderByAsc(ExecutionTaskDetailTb::getDetailNo));
    }

    private List<ExecutionTaskDetailTb> expand(String taskNo, String agentCode, String dimensionType,
            List<ExecutionTarget> targets, Date now) {
        if (targets == null || targets.isEmpty()) throw new BusinessException("targets不能为空");
        ExecutionDimensionTypeEnum type = ExecutionDimensionTypeEnum.fromCode(dimensionType)
                .orElseThrow(() -> new BusinessException("不支持的执行维度：" + dimensionType));
        Map<String, ExecutionTaskDetailTb> relations = new LinkedHashMap<>();
        for (ExecutionTarget target : targets) {
            switch (type) {
                case CUSTOMER -> expandCustomers(taskNo, agentCode, target, now, relations);
                case REGION -> expandRegions(taskNo, agentCode, target, now, relations);
                case INDUSTRY -> expandIndustries(taskNo, agentCode, target, now, relations);
            }
        }
        return new ArrayList<>(relations.values());
    }

    /** 客户维度中，地区和行业只是客户筛选条件；最终最小执行单位永远是一个真实客户。 */
    private void expandCustomers(String taskNo, String agentCode, ExecutionTarget target, Date now,
            Map<String, ExecutionTaskDetailTb> relations) {
        var query = Wrappers.<BaseCustomerTb>lambdaQuery();
        List<String> customerCodes = codes(target.customerCodes());
        if (!customerCodes.isEmpty()) {
            // 指定客户即为明确训练对象，地区和行业以客户主数据为准。
            query.in(BaseCustomerTb::getCustomerCode, customerCodes);
        } else {
            List<String> regionCodes = codes(target.regionCodes());
            List<String> industryCodes = codes(target.industryCodes());
            if (!regionCodes.isEmpty()) query.in(BaseCustomerTb::getRegionCode, regionCodes);
            if (!industryCodes.isEmpty()) query.in(BaseCustomerTb::getIndustryCode, industryCodes);
        }
        baseCustomerTbMapper.selectList(query).forEach(customer -> put(relations,
                relation(taskNo, agentCode, ExecutionDimensionTypeEnum.CUSTOMER, customer.getRegionCode(), customer.getIndustryCode(),
                        customer.getCustomerCode(), now)));
    }

    /** 地区维度不接受行业、客户条件，一个地区就是一个最小执行目标。 */
    private void expandRegions(String taskNo, String agentCode, ExecutionTarget target, Date now,
            Map<String, ExecutionTaskDetailTb> relations) {
        if (!codes(target.industryCodes()).isEmpty() || !codes(target.customerCodes()).isEmpty())
            throw new BusinessException("地区维度只允许传regionCodes");
        List<String> regionCodes = codes(target.regionCodes());
        if (regionCodes.isEmpty()) throw new BusinessException("地区维度必须传regionCodes");
        regionCodes.forEach(regionCode -> put(relations,
                relation(taskNo, agentCode, ExecutionDimensionTypeEnum.REGION, regionCode, "", "", now)));
    }

    /** 行业维度可按地区限制；未给行业时展开该地区（或全局）的全部实际行业。 */
    private void expandIndustries(String taskNo, String agentCode, ExecutionTarget target, Date now,
            Map<String, ExecutionTaskDetailTb> relations) {
        if (!codes(target.customerCodes()).isEmpty()) throw new BusinessException("行业维度不允许传customerCodes");
        List<String> regionCodes = codes(target.regionCodes());
        List<String> industryCodes = codes(target.industryCodes());
        if (!industryCodes.isEmpty()) {
            List<String> scopes = regionCodes.isEmpty() ? List.of("") : regionCodes;
            for (String regionCode : scopes) for (String industryCode : industryCodes)
                put(relations, relation(taskNo, agentCode, ExecutionDimensionTypeEnum.INDUSTRY, regionCode, industryCode, "", now));
            return;
        }
        var query = Wrappers.<BaseCustomerTb>lambdaQuery().select(BaseCustomerTb::getRegionCode, BaseCustomerTb::getIndustryCode);
        if (!regionCodes.isEmpty()) query.in(BaseCustomerTb::getRegionCode, regionCodes);
        baseCustomerTbMapper.selectList(query).forEach(customer -> put(relations,
                relation(taskNo, agentCode, ExecutionDimensionTypeEnum.INDUSTRY, customer.getRegionCode(), customer.getIndustryCode(), "", now)));
    }

    private ExecutionTaskDetailTb relation(String taskNo, String agentCode, ExecutionDimensionTypeEnum dimensionType,
            String regionCode, String industryCode, String customerCode, Date now) {
        ExecutionTaskDetailTb relation = new ExecutionTaskDetailTb();
        relation.setTaskNo(taskNo);
        relation.setAgentCode(agentCode);
        relation.setDimensionType(dimensionType.getCode());
        relation.setRegionCode(defaultValue(regionCode));
        relation.setIndustryCode(defaultValue(industryCode));
        relation.setCustomerCode(defaultValue(customerCode));
        relation.setStatus(ExecutionTargetStatusEnum.PENDING.getCode());
        relation.setCreatedAt(now);
        relation.setUpdatedAt(now);
        return relation;
    }

    private void put(Map<String, ExecutionTaskDetailTb> relations, ExecutionTaskDetailTb relation) {
        relations.putIfAbsent(relation.getDimensionType() + "|" + relation.getRegionCode() + "|"
                + relation.getIndustryCode() + "|" + relation.getCustomerCode(), relation);
    }

    private List<String> codes(List<String> values) {
        return values == null ? List.of() : values.stream().filter(TextUtils::hasText).distinct().toList();
    }

    private String defaultValue(String value) {
        return TextUtils.hasText(value) ? value : "";
    }

    private String newTaskNo() {
        String sequence = baseCodeGenerateService.nextCode(BaseCodeTypeEnum.TASK);
        String serialNo = sequence.substring(BaseCodeTypeEnum.TASK.prefix().length());
        return "TASK-" + LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE) + "-" + serialNo;
    }

    private void refreshTaskStatus(String taskNo) {
        ExecutionTaskTb task = executionTaskTbMapper.selectOne(Wrappers.<ExecutionTaskTb>lambdaQuery()
                .eq(ExecutionTaskTb::getTaskNo, taskNo).last("limit 1"));
        if (task == null) return;
        List<ExecutionTaskDetailTb> all = relations(taskNo);
        boolean running = all.stream().anyMatch(item -> !ExecutionTargetStatusEnum.SUCCESS.getCode().equals(item.getStatus())
                && !ExecutionTargetStatusEnum.FAILED.getCode().equals(item.getStatus())
                && !ExecutionTargetStatusEnum.CANCELLED.getCode().equals(item.getStatus()));
        boolean failed = all.stream().anyMatch(item -> ExecutionTargetStatusEnum.FAILED.getCode().equals(item.getStatus()));
        task.setStatus(running ? ExecutionTaskStatusEnum.RUNNING.getCode()
                : failed ? ExecutionTaskStatusEnum.FAILED.getCode() : ExecutionTaskStatusEnum.COMPLETED.getCode());
        Date now = new Date();
        if (!running) task.setFinishedAt(now);
        else if (task.getStartedAt() == null) task.setStartedAt(now);
        task.setUpdatedAt(now);
        executionTaskTbMapper.updateById(task);
    }

    private String json(List<ExecutionTarget> targets) {
        try {
            return objectMapper.writeValueAsString(targets);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("任务目标序列化失败");
        }
    }
}
