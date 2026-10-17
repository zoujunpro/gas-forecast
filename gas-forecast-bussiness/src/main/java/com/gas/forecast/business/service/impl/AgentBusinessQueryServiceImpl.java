package com.gas.forecast.business.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gas.forecast.business.service.AgentBusinessQueryService;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.core.PageInfoDTO;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.BaseCustomerTb;
import com.gas.forecast.dao.domain.BaseIndustryTb;
import com.gas.forecast.dao.domain.BaseRegionTb;
import com.gas.forecast.dao.domain.ModelForecastRecordTb;
import com.gas.forecast.dao.domain.ModelForecastResultTb;
import com.gas.forecast.dao.domain.ModelTrainRecordTb;
import com.gas.forecast.dao.domain.AgentExecutionTaskTb;
import com.gas.forecast.dao.mapper.AgentExecutionTaskTbMapper;
import com.gas.forecast.dao.mapper.BaseCustomerTbMapper;
import com.gas.forecast.dao.mapper.BaseIndustryTbMapper;
import com.gas.forecast.dao.mapper.BaseRegionTbMapper;
import com.gas.forecast.dao.mapper.ModelForecastRecordTbMapper;
import com.gas.forecast.dao.mapper.ModelForecastResultTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainRecordTbMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 复用业务基础表和训练预测记录表实现智能体分页查询。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentBusinessQueryServiceImpl implements AgentBusinessQueryService {
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final int MAX_FETCH_ALL_SIZE = 5000;
    private final BaseRegionTbMapper regionMapper;
    private final BaseIndustryTbMapper industryMapper;
    private final BaseCustomerTbMapper customerMapper;
    private final ModelTrainRecordTbMapper trainRecordMapper;
    private final ModelForecastRecordTbMapper forecastRecordMapper;
    private final ModelForecastResultTbMapper forecastResultMapper;
    private final AgentExecutionTaskTbMapper taskMapper;

    @Override
    public Object regions(String keyword, Integer pageNo, Integer pageSize, boolean fetchAll) {
        var query = Wrappers.<BaseRegionTb>lambdaQuery();
        if (TextUtils.hasText(keyword)) query.and(row -> row.like(BaseRegionTb::getRegionCode, keyword).or().like(BaseRegionTb::getRegionName, keyword));
        query.orderByAsc(BaseRegionTb::getRegionName).orderByAsc(BaseRegionTb::getId);
        if (fetchAll) return all(regionMapper.selectList(query.last("limit " + (MAX_FETCH_ALL_SIZE + 1))));
        IPage<BaseRegionTb> page = regionMapper.selectPage(page(pageNo, pageSize), query);
        return PageInfoDTO.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public Object industries(String keyword, Integer pageNo, Integer pageSize, boolean fetchAll) {
        var query = Wrappers.<BaseIndustryTb>lambdaQuery();
        if (TextUtils.hasText(keyword)) query.and(row -> row.like(BaseIndustryTb::getIndustryCode, keyword).or().like(BaseIndustryTb::getIndustryName, keyword));
        query.orderByAsc(BaseIndustryTb::getIndustryName).orderByAsc(BaseIndustryTb::getId);
        if (fetchAll) return all(industryMapper.selectList(query.last("limit " + (MAX_FETCH_ALL_SIZE + 1))));
        IPage<BaseIndustryTb> page = industryMapper.selectPage(page(pageNo, pageSize), query);
        return PageInfoDTO.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public Object customers(String keyword, String regionCode, String industryCode, Integer pageNo, Integer pageSize, boolean fetchAll) {
        var query = Wrappers.<BaseCustomerTb>lambdaQuery();
        if (TextUtils.hasText(keyword)) query.and(row -> row.like(BaseCustomerTb::getCustomerCode, keyword).or().like(BaseCustomerTb::getCustomerName, keyword));
        if (TextUtils.hasText(regionCode)) query.eq(BaseCustomerTb::getRegionCode, regionCode);
        if (TextUtils.hasText(industryCode)) query.eq(BaseCustomerTb::getIndustryCode, industryCode);
        query.orderByAsc(BaseCustomerTb::getCustomerName).orderByAsc(BaseCustomerTb::getId);
        if (fetchAll) return all(customerMapper.selectList(query.last("limit " + (MAX_FETCH_ALL_SIZE + 1))));
        IPage<BaseCustomerTb> page = customerMapper.selectPage(page(pageNo, pageSize), query);
        return PageInfoDTO.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    private <T> Map<String, Object> all(List<T> records) {
        if (records.size() > MAX_FETCH_ALL_SIZE)
            throw new BusinessException("查询结果超过" + MAX_FETCH_ALL_SIZE + "条，请增加筛选条件后重试");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", records.size());
        result.put("page", 1);
        result.put("size", records.size());
        result.put("fetchedCount", records.size());
        result.put("allFetched", true);
        return result;
    }

    @Override
    public Object latestTraining(String customerCode, String agentCode) {
        ModelTrainRecordTb record = trainRecordMapper.selectOne(trainingQuery(customerCode, agentCode, null).orderByDesc(ModelTrainRecordTb::getCreatedAt)
                .orderByDesc(ModelTrainRecordTb::getId).last("limit 1"));
        if (record == null) throw new BusinessException("没有找到该客户的训练记录");
        return record;
    }

    @Override
    public Object trainingHistory(String customerCode, String agentCode, String status, Integer pageNo, Integer pageSize) {
        var query = trainingQuery(customerCode, agentCode, status).orderByDesc(ModelTrainRecordTb::getCreatedAt).orderByDesc(ModelTrainRecordTb::getId);
        IPage<ModelTrainRecordTb> page = trainRecordMapper.selectPage(page(pageNo, pageSize), query);
        return PageInfoDTO.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public Object latestForecast(String customerCode, String agentCode, boolean includePoints) {
        ModelForecastRecordTb record = forecastRecordMapper.selectOne(forecastQuery(customerCode, agentCode, null).orderByDesc(ModelForecastRecordTb::getCreatedAt)
                .orderByDesc(ModelForecastRecordTb::getId).last("limit 1"));
        if (record == null) throw new BusinessException("没有找到该客户的预测记录");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("record", record);
        if (includePoints) result.put("points", forecastResultMapper.selectList(Wrappers.<ModelForecastResultTb>lambdaQuery()
                .eq(ModelForecastResultTb::getForecastBatchNo, record.getForecastBatchNo()).orderByAsc(ModelForecastResultTb::getForecastDate)));
        return result;
    }

    @Override
    public Object forecastHistory(String customerCode, String agentCode, Integer status, Integer pageNo, Integer pageSize) {
        var query = forecastQuery(customerCode, agentCode, status).orderByDesc(ModelForecastRecordTb::getCreatedAt).orderByDesc(ModelForecastRecordTb::getId);
        IPage<ModelForecastRecordTb> page = forecastRecordMapper.selectPage(page(pageNo, pageSize), query);
        return PageInfoDTO.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public Object resolveBusinessReference(String reference) {
        if (!TextUtils.hasText(reference)) throw new BusinessException("reference不能为空");
        String value = reference.trim();
        ModelTrainRecordTb training = trainRecordMapper.selectOne(Wrappers.<ModelTrainRecordTb>lambdaQuery()
                .eq(ModelTrainRecordTb::getBatchNo, value).last("limit 1"));
        if (training != null) {
            Map<String, Object> result = reference("TRAINING_BATCH", value, training.getStatus(), training.getAgentCode());
            result.put("customerCode", training.getCustomerCode());
            result.put("customerName", training.getCustomerName());
            result.put("supportedActions", List.of("STATUS", "RESULT"));
            return result;
        }
        ModelForecastRecordTb forecast = forecastRecordMapper.selectOne(Wrappers.<ModelForecastRecordTb>lambdaQuery()
                .eq(ModelForecastRecordTb::getForecastBatchNo, value).last("limit 1"));
        if (forecast != null) {
            Map<String, Object> result = reference("FORECAST_BATCH", value, forecast.getStatus(), forecast.getAgentCode());
            result.put("customerCode", forecast.getCustomerCode());
            result.put("customerName", forecast.getCustomerName());
            result.put("supportedActions", List.of("STATUS", "RESULT"));
            return result;
        }
        AgentExecutionTaskTb task = taskMapper.selectOne(Wrappers.<AgentExecutionTaskTb>lambdaQuery()
                .eq(AgentExecutionTaskTb::getTaskNo, value).last("limit 1"));
        if (task != null) {
            Map<String, Object> result = reference("TASK", value, task.getStatus(), task.getAgentCode());
            result.put("taskType", task.getTaskType());
            result.put("customerCode", task.getCustomerCode());
            result.put("customerName", task.getCustomerName());
            result.put("supportedActions", List.of("STATUS", "STEPS", "RESUME", "RETRY"));
            return result;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("found", false);
        result.put("reference", value);
        result.put("actualType", "UNKNOWN");
        result.put("supportedActions", List.of());
        return result;
    }

    private Map<String, Object> reference(String type, String reference, Object status, String agentCode) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("found", true);
        result.put("reference", reference);
        result.put("actualType", type);
        result.put("status", status);
        result.put("agentCode", agentCode);
        return result;
    }

    private com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ModelTrainRecordTb> trainingQuery(String customerCode, String agentCode, String status) {
        if (!TextUtils.hasText(customerCode)) throw new BusinessException("customerCode不能为空");
        var query = Wrappers.<ModelTrainRecordTb>lambdaQuery().eq(ModelTrainRecordTb::getCustomerCode, customerCode);
        if (TextUtils.hasText(agentCode)) query.eq(ModelTrainRecordTb::getAgentCode, agentCode);
        if (TextUtils.hasText(status)) query.eq(ModelTrainRecordTb::getStatus, status);
        return query;
    }

    private com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ModelForecastRecordTb> forecastQuery(String customerCode, String agentCode, Integer status) {
        if (!TextUtils.hasText(customerCode)) throw new BusinessException("customerCode不能为空");
        var query = Wrappers.<ModelForecastRecordTb>lambdaQuery().eq(ModelForecastRecordTb::getCustomerCode, customerCode);
        if (TextUtils.hasText(agentCode)) query.eq(ModelForecastRecordTb::getAgentCode, agentCode);
        if (status != null) query.eq(ModelForecastRecordTb::getStatus, status);
        return query;
    }

    private <T> Page<T> page(Integer pageNo, Integer pageSize) {
        int number = pageNo == null ? 1 : Math.max(1, pageNo);
        int size = pageSize == null ? DEFAULT_SIZE : Math.max(1, Math.min(MAX_SIZE, pageSize));
        return new Page<>(number, size);
    }
}
