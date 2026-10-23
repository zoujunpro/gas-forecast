package com.gas.forecast.business.application.forecast;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.ModelBacktestResultTb;
import com.gas.forecast.dao.domain.ModelExecuteRecordTb;
import com.gas.forecast.dao.domain.ModelForecastResultTb;
import com.gas.forecast.dao.mapper.ModelBacktestResultTbMapper;
import com.gas.forecast.dao.mapper.ModelForecastResultTbMapper;
import java.math.BigDecimal;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 将模型平台返回的回测、预测点规范化为可查询的业务结果明细。 */
@Service
@RequiredArgsConstructor
public class ForecastExecutionResultPersistenceService {
    private final ModelBacktestResultTbMapper modelBacktestResultTbMapper;
    private final ModelForecastResultTbMapper modelForecastResultTbMapper;

    /** 预测编号稳定，失败重试时用本次成功结果完整覆盖旧明细。 */
    @Transactional
    public void replace(ModelExecuteRecordTb record, JsonNode result) {
        String forecastNo = record.getForecastNo();
        if (!TextUtils.hasText(forecastNo)) return;
        modelBacktestResultTbMapper.delete(Wrappers.<ModelBacktestResultTb>lambdaQuery()
                .eq(ModelBacktestResultTb::getForecastNo, forecastNo));
        modelForecastResultTbMapper.delete(Wrappers.<ModelForecastResultTb>lambdaQuery()
                .eq(ModelForecastResultTb::getForecastNo, forecastNo));

        Date now = new Date();
        for (JsonNode point : points(result.path("backtest"), "points", "rolling_backtest_results")) {
            String date = text(point, "train_date", "stat_date", "date", "statDate");
            BigDecimal actual = decimal(point, "actual_value", "actualValue", "actual");
            BigDecimal predicted = decimal(point, "predicted_value", "predictedValue", "prediction", "predicted");
            if (!TextUtils.hasText(date) || actual == null || predicted == null) continue;
            ModelBacktestResultTb item = new ModelBacktestResultTb();
            item.setForecastNo(forecastNo);
            item.setTrainDate(date);
            item.setActualValue(actual);
            item.setPredictedValue(predicted);
            item.setCreatedAt(now);
            modelBacktestResultTbMapper.insert(item);
        }
        for (JsonNode point : points(result.path("forecast"), "points")) {
            String date = text(point, "forecast_date", "forecastDate", "date", "stat_date");
            BigDecimal value = decimal(point, "forecast_value", "forecastValue", "prediction", "value");
            if (!TextUtils.hasText(date) || value == null) continue;
            ModelForecastResultTb item = new ModelForecastResultTb();
            item.setForecastNo(forecastNo);
            item.setForecastDate(date);
            item.setForecastValue(value);
            item.setCreatedAt(now);
            modelForecastResultTbMapper.insert(item);
        }
    }

    private JsonNode points(JsonNode parent, String... fields) {
        if (parent == null || parent.isMissingNode()) return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
        for (String field : fields) if (parent.path(field).isArray()) return parent.path(field);
        return parent.isArray() ? parent : com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
    }

    private String text(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = node.path(field).asText(null);
            if (TextUtils.hasText(value)) return value;
        }
        return null;
    }

    private BigDecimal decimal(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isNumber()) return value.decimalValue();
            if (value.isTextual()) try { return new BigDecimal(value.asText()); } catch (NumberFormatException ignored) { }
        }
        return null;
    }
}
