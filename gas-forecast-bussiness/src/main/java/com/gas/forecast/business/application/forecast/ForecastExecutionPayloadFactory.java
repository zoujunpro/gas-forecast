package com.gas.forecast.business.application.forecast;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.DataDailySalesTb;
import com.gas.forecast.dao.domain.ExecutionTaskDetailTb;
import com.gas.forecast.dao.domain.ExecutionTaskTb;
import com.gas.forecast.dao.domain.ModelExecuteRecordTb;
import com.gas.forecast.dao.mapper.DataDailySalesTbMapper;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 为统一模型 /execute 接口构建严格分离的历史数据和未来数据。 */
@Service
@RequiredArgsConstructor
public class ForecastExecutionPayloadFactory {
    private final ObjectMapper objectMapper;
    private final DataDailySalesTbMapper dataDailySalesTbMapper;

    public ObjectNode build(ExecutionTaskTb task, ExecutionTaskDetailTb detail, ModelExecuteRecordTb record) {
        JsonNode snapshot = readSnapshot(task);
        String unit = unit(snapshot.path("forecast").path("forecastUnit").asText(null), detail.getAgentCode());
        int horizon = snapshot.path("forecast").path("forecastHorizon").asInt(defaultHorizon(detail.getAgentCode()));
        LocalDate start = start(snapshot.path("forecast").path("forecastStartDate").asText(null), unit);
        JsonNode range = snapshot.path("trainingDataRange");

        var query = Wrappers.<DataDailySalesTb>lambdaQuery().isNotNull(DataDailySalesTb::getGasSales);
        if (TextUtils.hasText(detail.getCustomerCode())) query.eq(DataDailySalesTb::getCustomerCode, detail.getCustomerCode());
        else {
            if (TextUtils.hasText(detail.getRegionCode())) query.eq(DataDailySalesTb::getRegionCode, detail.getRegionCode());
            if (TextUtils.hasText(detail.getIndustryCode())) query.eq(DataDailySalesTb::getIndustryCode, detail.getIndustryCode());
        }
        String historyStart = range.path("historyStartDate").asText(null);
        String historyEnd = range.path("historyEndDate").asText(null);
        if (TextUtils.hasText(historyStart)) query.ge(DataDailySalesTb::getStatDate, historyStart);
        if (TextUtils.hasText(historyEnd)) query.le(DataDailySalesTb::getStatDate, historyEnd);
        List<DataDailySalesTb> sales = dataDailySalesTbMapper.selectList(query.orderByAsc(DataDailySalesTb::getStatDate).orderByAsc(DataDailySalesTb::getId));
        if (sales.isEmpty()) throw new BusinessException("当前预测对象没有可用的历史销量数据");

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("forecast_no", record.getForecastNo());
        payload.put("agent_code", detail.getAgentCode());
        payload.put("model_code", record.getModelCode());
        put(payload, "region_code", detail.getRegionCode()); put(payload, "industry_code", detail.getIndustryCode()); put(payload, "customer_code", detail.getCustomerCode());
        payload.put("forecast_horizon", horizon); payload.put("forecast_unit", unit);
        payload.set("params", objectMapper.createObjectNode());
        ArrayNode history = payload.putArray("history_dataset");
        for (DataDailySalesTb item : sales) {
            ObjectNode row = history.addObject(); row.put("date", item.getStatDate()); row.put("gas_sales", item.getGasSales());
        }
        ArrayNode future = payload.putArray("future_dataset");
        for (int index = 0; index < horizon; index++) future.addObject().put("date", next(start, unit, index).toString());
        record.setHistoryRows(sales.size()); record.setHistoryStartDate(LocalDate.parse(sales.get(0).getStatDate()));
        record.setHistoryEndDate(LocalDate.parse(sales.get(sales.size() - 1).getStatDate()));
        record.setForecastStartDate(start); record.setForecastHorizon(horizon); record.setForecastUnit(unit);
        return payload;
    }

    private JsonNode readSnapshot(ExecutionTaskTb task) {
        try { return task == null || !TextUtils.hasText(task.getRequestParam()) ? objectMapper.createObjectNode() : objectMapper.readTree(task.getRequestParam()); }
        catch (Exception exception) { throw new BusinessException("预测任务参数快照无法解析"); }
    }
    private String unit(String requested, String agentCode) {
        if (TextUtils.hasText(requested)) return switch (requested.toLowerCase()) { case "day", "daily" -> "day"; case "month", "monthly" -> "month"; case "tenday" -> "tenday"; default -> throw new BusinessException("不支持的预测单位：" + requested); };
        return "monthly-sales".equals(agentCode) ? "month" : "winter-supply".equals(agentCode) ? "tenday" : "day";
    }
    private int defaultHorizon(String agentCode) { return "monthly-sales".equals(agentCode) ? 12 : "winter-supply".equals(agentCode) ? 18 : 30; }
    private LocalDate start(String value, String unit) {
        if (TextUtils.hasText(value)) return LocalDate.parse(value);
        LocalDate today = LocalDate.now();
        return "month".equals(unit) ? today.withDayOfMonth(1) : "tenday".equals(unit) ? today.withDayOfMonth(today.getDayOfMonth() <= 10 ? 1 : today.getDayOfMonth() <= 20 ? 11 : 21) : today;
    }
    private LocalDate next(LocalDate start, String unit, int index) { return "month".equals(unit) ? start.plusMonths(index) : "tenday".equals(unit) ? start.plusDays(index * 10L) : start.plusDays(index); }
    private void put(ObjectNode node, String name, String value) { if (TextUtils.hasText(value)) node.put(name, value); }
}
