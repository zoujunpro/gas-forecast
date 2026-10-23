package com.gas.forecast.business.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.business.dto.request.ExecutionTarget;
import com.gas.forecast.business.enums.ExecutionTargetStatus;
import com.gas.forecast.business.enums.ExecutionBusinessType;
import com.gas.forecast.business.enums.BaseCodeType;
import com.gas.forecast.business.enums.ExecutionDimensionType;
import com.gas.forecast.business.enums.ExecutionTaskStatus;
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
    public ExecutionTaskTb create(String conversationNo, ExecutionBusinessType businessType, String agentCode,
            String dimensionType, List<ExecutionTarget> targets, String taskName) {
        Date now = new Date();
        ExecutionTaskTb task = new ExecutionTaskTb();
        task.setTaskNo(newTaskNo());
        task.setConversationNo(conversationNo);
        task.setBusinessType(businessType.getCode());
        task.setDimensionType(dimensionType);
        task.setTaskName(taskName);
        task.setScopeConditionJson(json(targets));
        task.setStatus(ExecutionTaskStatus.PENDING.getCode());
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

    /** 回填最小目标的训练或预测批次号并推进关联状态。 */
    @Transactional
    public void updateBatch(Long relationId, String trainBatchNo, String forecastBatchNo, ExecutionTargetStatus status) {
        ExecutionTaskDetailTb relation = executionTaskDetailTbMapper.selectById(relationId);
        if (relation == null) throw new BusinessException("任务目标关联不存在");
        if (TextUtils.hasText(trainBatchNo)) relation.setTrainBatchNo(trainBatchNo);
        if (TextUtils.hasText(forecastBatchNo)) relation.setForecastBatchNo(forecastBatchNo);
        relation.setStatus(status.getCode());
        relation.setUpdatedAt(new Date());
        executionTaskDetailTbMapper.updateById(relation);
        refreshTaskStatus(relation.getTaskNo());
    }

    public List<ExecutionTaskDetailTb> relations(String taskNo) {
        return executionTaskDetailTbMapper.selectList(Wrappers.<ExecutionTaskDetailTb>lambdaQuery()
                .eq(ExecutionTaskDetailTb::getTaskNo, taskNo)
                .orderByAsc(ExecutionTaskDetailTb::getDetailNo));
    }

    public List<ExecutionTaskDetailTb> relationsByTrainBatch(String trainBatchNo) {
        return executionTaskDetailTbMapper.selectList(Wrappers.<ExecutionTaskDetailTb>lambdaQuery()
                .eq(ExecutionTaskDetailTb::getTrainBatchNo, trainBatchNo)
                .orderByAsc(ExecutionTaskDetailTb::getId));
    }

    /** 训练结束后更新关联行；预测任务仅进入等待预测阶段，不创建第二个训练任务。 */
    @Transactional
    public void markTrainingCompleted(String trainBatchNo, boolean success) {
        List<ExecutionTaskDetailTb> relations = executionTaskDetailTbMapper.selectList(Wrappers.<ExecutionTaskDetailTb>lambdaQuery()
                .eq(ExecutionTaskDetailTb::getTrainBatchNo, trainBatchNo));
        Date now = new Date();
        for (ExecutionTaskDetailTb relation : relations) {
            ExecutionTaskTb task = executionTaskTbMapper.selectOne(Wrappers.<ExecutionTaskTb>lambdaQuery()
                    .eq(ExecutionTaskTb::getTaskNo, relation.getTaskNo()).last("limit 1"));
            boolean forecastIntent = task != null && ExecutionBusinessType.FORECAST.getCode().equals(task.getBusinessType());
            relation.setStatus(success
                    ? (forecastIntent ? ExecutionTargetStatus.WAITING_FORECAST.getCode() : ExecutionTargetStatus.SUCCESS.getCode())
                    : ExecutionTargetStatus.FAILED.getCode());
            relation.setUpdatedAt(now);
            executionTaskDetailTbMapper.updateById(relation);
            if (task != null) refreshTaskStatus(task.getTaskNo());
        }
    }

    /** 预测结束后按预测批次回写关联目标，并聚合更新所属任务。 */
    @Transactional
    public void markForecastCompleted(String forecastBatchNo, boolean success) {
        List<ExecutionTaskDetailTb> relations = executionTaskDetailTbMapper.selectList(Wrappers.<ExecutionTaskDetailTb>lambdaQuery()
                .eq(ExecutionTaskDetailTb::getForecastBatchNo, forecastBatchNo));
        Date now = new Date();
        for (ExecutionTaskDetailTb relation : relations) {
            relation.setStatus(success ? ExecutionTargetStatus.SUCCESS.getCode() : ExecutionTargetStatus.FAILED.getCode());
            relation.setUpdatedAt(now);
            executionTaskDetailTbMapper.updateById(relation);
            refreshTaskStatus(relation.getTaskNo());
        }
    }

    private List<ExecutionTaskDetailTb> expand(String taskNo, String agentCode, String dimensionType,
            List<ExecutionTarget> targets, Date now) {
        if (targets == null || targets.isEmpty()) throw new BusinessException("targets不能为空");
        ExecutionDimensionType type = ExecutionDimensionType.fromCode(dimensionType)
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
                relation(taskNo, agentCode, ExecutionDimensionType.CUSTOMER, customer.getRegionCode(), customer.getIndustryCode(),
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
                relation(taskNo, agentCode, ExecutionDimensionType.REGION, regionCode, "", "", now)));
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
                put(relations, relation(taskNo, agentCode, ExecutionDimensionType.INDUSTRY, regionCode, industryCode, "", now));
            return;
        }
        var query = Wrappers.<BaseCustomerTb>lambdaQuery().select(BaseCustomerTb::getRegionCode, BaseCustomerTb::getIndustryCode);
        if (!regionCodes.isEmpty()) query.in(BaseCustomerTb::getRegionCode, regionCodes);
        baseCustomerTbMapper.selectList(query).forEach(customer -> put(relations,
                relation(taskNo, agentCode, ExecutionDimensionType.INDUSTRY, customer.getRegionCode(), customer.getIndustryCode(), "", now)));
    }

    private ExecutionTaskDetailTb relation(String taskNo, String agentCode, ExecutionDimensionType dimensionType,
            String regionCode, String industryCode, String customerCode, Date now) {
        ExecutionTaskDetailTb relation = new ExecutionTaskDetailTb();
        relation.setTaskNo(taskNo);
        relation.setAgentCode(agentCode);
        relation.setDimensionType(dimensionType.getCode());
        relation.setRegionCode(defaultValue(regionCode));
        relation.setIndustryCode(defaultValue(industryCode));
        relation.setCustomerCode(defaultValue(customerCode));
        relation.setStatus(ExecutionTargetStatus.PENDING.getCode());
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
        String sequence = baseCodeGenerateService.nextCode(BaseCodeType.TASK);
        String serialNo = sequence.substring(BaseCodeType.TASK.prefix().length());
        return "TASK-" + LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE) + "-" + serialNo;
    }

    private void refreshTaskStatus(String taskNo) {
        ExecutionTaskTb task = executionTaskTbMapper.selectOne(Wrappers.<ExecutionTaskTb>lambdaQuery()
                .eq(ExecutionTaskTb::getTaskNo, taskNo).last("limit 1"));
        if (task == null) return;
        List<ExecutionTaskDetailTb> all = relations(taskNo);
        boolean running = all.stream().anyMatch(item -> !ExecutionTargetStatus.SUCCESS.getCode().equals(item.getStatus())
                && !ExecutionTargetStatus.FAILED.getCode().equals(item.getStatus())
                && !ExecutionTargetStatus.CANCELLED.getCode().equals(item.getStatus()));
        boolean failed = all.stream().anyMatch(item -> ExecutionTargetStatus.FAILED.getCode().equals(item.getStatus()));
        task.setStatus(running ? ExecutionTaskStatus.RUNNING.getCode()
                : failed ? ExecutionTaskStatus.FAILED.getCode() : ExecutionTaskStatus.COMPLETED.getCode());
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
