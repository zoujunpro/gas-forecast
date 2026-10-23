package com.gas.forecast.business.application.mcp;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gas.forecast.business.application.mcp.AgentBusinessQueryService;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.core.PageInfoDTO;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.BaseCustomerTb;
import com.gas.forecast.dao.domain.BaseIndustryTb;
import com.gas.forecast.dao.domain.BaseRegionTb;
import com.gas.forecast.dao.domain.ExecutionTaskTb;
import com.gas.forecast.dao.domain.ModelExecuteRecordTb;
import com.gas.forecast.dao.mapper.BaseCustomerTbMapper;
import com.gas.forecast.dao.mapper.BaseIndustryTbMapper;
import com.gas.forecast.dao.mapper.BaseRegionTbMapper;
import com.gas.forecast.dao.mapper.ExecutionTaskTbMapper;
import com.gas.forecast.dao.mapper.ModelExecuteRecordTbMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 为智能体提供基础资料和统一预测执行记录的只读查询。 */
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
    private final ModelExecuteRecordTbMapper executeRecordMapper;
    private final ExecutionTaskTbMapper taskMapper;

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

    @Override
    public Object latestForecast(String customerCode, String regionCode, String industryCode, String agentCode, boolean includePoints) {
        ModelExecuteRecordTb record = latestExecution(customerCode, regionCode, industryCode, agentCode);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("record", record);
        result.put("training", record.getTrainingResultJson());
        result.put("backtest", record.getBacktestResultJson());
        result.put("forecast", record.getForecastResultJson());
        return result;
    }

    @Override
    public Object resolveBusinessReference(String reference) {
        if (!TextUtils.hasText(reference)) throw new BusinessException("reference不能为空");
        String value = reference.trim();
        ModelExecuteRecordTb execution = executeRecordMapper.selectOne(Wrappers.<ModelExecuteRecordTb>lambdaQuery()
                .eq(ModelExecuteRecordTb::getForecastNo, value).last("limit 1"));
        if (execution != null) {
            Map<String, Object> result = reference("FORECAST", value, execution.getStatus(), execution.getAgentCode());
            result.put("customerCode", execution.getCustomerCode());
            result.put("customerName", execution.getCustomerName());
            result.put("supportedActions", List.of("STATUS", "RESULT"));
            return result;
        }
        ExecutionTaskTb task = taskMapper.selectOne(Wrappers.<ExecutionTaskTb>lambdaQuery()
                .eq(ExecutionTaskTb::getTaskNo, value).last("limit 1"));
        if (task != null) {
            Map<String, Object> result = reference("TASK", value, task.getStatus(), task.getAgentCode());
            result.put("supportedActions", List.of("STATUS", "STEPS"));
            return result;
        }
        return Map.of("found", false, "reference", value, "actualType", "UNKNOWN", "supportedActions", List.of());
    }

    private ModelExecuteRecordTb latestExecution(String customerCode, String regionCode, String industryCode, String agentCode) {
        var query = Wrappers.<ModelExecuteRecordTb>lambdaQuery();
        if (TextUtils.hasText(customerCode)) query.eq(ModelExecuteRecordTb::getCustomerCode, customerCode);
        if (TextUtils.hasText(regionCode)) query.eq(ModelExecuteRecordTb::getRegionCode, regionCode);
        if (TextUtils.hasText(industryCode)) query.eq(ModelExecuteRecordTb::getIndustryCode, industryCode);
        if (TextUtils.hasText(agentCode)) query.eq(ModelExecuteRecordTb::getAgentCode, agentCode);
        ModelExecuteRecordTb record = executeRecordMapper.selectOne(query.orderByDesc(ModelExecuteRecordTb::getCreatedAt)
                .orderByDesc(ModelExecuteRecordTb::getId).last("limit 1"));
        if (record == null) throw new BusinessException("没有找到符合条件的预测执行记录");
        return record;
    }

    private <T> Map<String, Object> all(List<T> records) {
        if (records.size() > MAX_FETCH_ALL_SIZE) throw new BusinessException("查询结果超过" + MAX_FETCH_ALL_SIZE + "条，请增加筛选条件后重试");
        return Map.of("records", records, "total", records.size(), "page", 1, "size", records.size(), "fetchedCount", records.size(), "allFetched", true);
    }

    private Map<String, Object> reference(String type, String reference, Object status, String agentCode) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("found", true); result.put("reference", reference); result.put("actualType", type);
        result.put("status", status); result.put("agentCode", agentCode);
        return result;
    }

    private <T> Page<T> page(Integer pageNo, Integer pageSize) {
        return new Page<>(pageNo == null ? 1 : Math.max(1, pageNo), pageSize == null ? DEFAULT_SIZE : Math.max(1, Math.min(MAX_SIZE, pageSize)));
    }
}
