package com.gas.forecast.business.application.forecast;

import com.gas.forecast.business.application.task.ExecutionTaskLifecycleService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.ExecutionTaskDetailTb;
import com.gas.forecast.dao.domain.ExecutionTaskTb;
import com.gas.forecast.dao.domain.ModelExecuteRecordTb;
import com.gas.forecast.dao.domain.BaseCustomerTb;
import com.gas.forecast.dao.domain.BaseIndustryTb;
import com.gas.forecast.dao.domain.BaseRegionTb;
import com.gas.forecast.dao.mapper.BaseCustomerTbMapper;
import com.gas.forecast.dao.mapper.BaseIndustryTbMapper;
import com.gas.forecast.dao.mapper.BaseRegionTbMapper;
import com.gas.forecast.dao.mapper.ModelExecuteRecordTbMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 维护任务明细与稳定预测执行记录的一对一关系。 */
@Service
@RequiredArgsConstructor
public class ForecastExecutionRecordService {
    private static final DateTimeFormatter FORECAST_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    /** 平台的自动候选模型编码；AUTO 只是业务侧训练偏好，不能透传给模型平台。 */
    private static final String DEFAULT_PLATFORM_MODEL_CODE = "MODEL_ALPHA_AUTO_V1.0";

    private final ModelExecuteRecordTbMapper modelExecuteRecordTbMapper;
    private final ExecutionTaskLifecycleService taskLifecycleService;
    private final BaseCustomerTbMapper baseCustomerTbMapper;
    private final BaseRegionTbMapper baseRegionTbMapper;
    private final BaseIndustryTbMapper baseIndustryTbMapper;

    /** 首次插入执行记录；失败重试清空同一记录的结果后再次使用。 */
    @Transactional
    public ModelExecuteRecordTb prepare(ExecutionTaskDetailTb detail, ExecutionTaskTb task) {
        if (detail == null || detail.getId() == null) throw new BusinessException("任务明细不存在");
        String forecastNo = detail.getForecastNo();
        if (!TextUtils.hasText(forecastNo)) {
            forecastNo = "FORECAST-" + LocalDateTime.now().format(FORECAST_TIME) + "-" + String.format("%06d", detail.getId());
            taskLifecycleService.bindForecastNo(detail.getId(), forecastNo);
            detail.setForecastNo(forecastNo);
        }
        ModelExecuteRecordTb record = modelExecuteRecordTbMapper.selectOne(Wrappers.<ModelExecuteRecordTb>lambdaQuery()
                .eq(ModelExecuteRecordTb::getTaskDetailId, detail.getId()).last("limit 1"));
        Date now = new Date();
        if (record == null) {
            record = new ModelExecuteRecordTb();
            record.setForecastNo(forecastNo);
            record.setTaskDetailId(detail.getId());
            record.setAgentCode(detail.getAgentCode());
            record.setModelCode(DEFAULT_PLATFORM_MODEL_CODE);
            record.setRegionCode(detail.getRegionCode());
            record.setIndustryCode(detail.getIndustryCode());
            record.setCustomerCode(detail.getCustomerCode());
            fillScopeNames(record);
            record.setRequestParam(task == null ? null : task.getRequestParam());
            record.setStatus("RUNNING");
            record.setStartedAt(now);
            record.setCreatedAt(now);
            record.setUpdatedAt(now);
            modelExecuteRecordTbMapper.insert(record);
            return record;
        }
        if (!forecastNo.equals(record.getForecastNo())) throw new BusinessException("执行记录与任务明细的预测编号不一致");
        record.setStatus("RUNNING");
        record.setStartedAt(now);
        record.setFinishedAt(null);
        record.setErrorMessage(null);
        record.setTrainingResultJson(null);
        record.setBacktestResultJson(null);
        record.setForecastResultJson(null);
        record.setBestModel(null);
        record.setCandidateCount(null);
        record.setSuccessfulCandidateCount(null);
        record.setMape(null); record.setWmape(null); record.setSmape(null);
        record.setRmse(null); record.setMae(null); record.setR2(null);
        record.setExecuteDurationSeconds(null);
        // 兼容已创建但尚未成功的旧执行记录，避免将业务偏好 AUTO 传给模型平台。
        if (!TextUtils.hasText(record.getModelCode()) || "AUTO".equalsIgnoreCase(record.getModelCode())) {
            record.setModelCode(DEFAULT_PLATFORM_MODEL_CODE);
        }
        record.setRequestParam(task == null ? record.getRequestParam() : task.getRequestParam());
        fillScopeNames(record);
        record.setUpdatedAt(now);
        modelExecuteRecordTbMapper.updateById(record);
        return record;
    }

    /** 执行记录保存名称快照，页面展示不可依赖运行时再用名称反查编码。 */
    private void fillScopeNames(ModelExecuteRecordTb record) {
        if (TextUtils.hasText(record.getCustomerCode())) {
            BaseCustomerTb customer = baseCustomerTbMapper.selectOne(Wrappers.<BaseCustomerTb>lambdaQuery()
                    .eq(BaseCustomerTb::getCustomerCode, record.getCustomerCode()).last("limit 1"));
            if (customer != null) {
                record.setCustomerName(customer.getCustomerName());
                record.setRegionCode(customer.getRegionCode()); record.setRegionName(customer.getRegionName());
                record.setIndustryCode(customer.getIndustryCode()); record.setIndustryName(customer.getIndustryName());
                return;
            }
        }
        if (TextUtils.hasText(record.getRegionCode())) {
            BaseRegionTb region = baseRegionTbMapper.selectOne(Wrappers.<BaseRegionTb>lambdaQuery()
                    .eq(BaseRegionTb::getRegionCode, record.getRegionCode()).last("limit 1"));
            if (region != null) record.setRegionName(region.getRegionName());
        }
        if (TextUtils.hasText(record.getIndustryCode())) {
            BaseIndustryTb industry = baseIndustryTbMapper.selectOne(Wrappers.<BaseIndustryTb>lambdaQuery()
                    .eq(BaseIndustryTb::getIndustryCode, record.getIndustryCode()).last("limit 1"));
            if (industry != null) record.setIndustryName(industry.getIndustryName());
        }
    }
}
