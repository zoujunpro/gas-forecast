package com.gas.forecast.business.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.dao.domain.ModelTrainFeatureDataTb;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

/** 训练数据进入队列和模型平台前共用的最低质量门禁。 */
@Component
public class TrainingDataQualityValidator {

    public void validate(List<ModelTrainFeatureDataTb> rows) {
        if (rows == null || rows.isEmpty()) {
            throw new BusinessException("训练时间范围和模型作用范围内没有可用训练数据");
        }
        boolean hasTargetValue = rows.stream().anyMatch(row -> row.getGasSales() != null);
        if (!hasTargetValue) {
            throw new BusinessException("训练目标销量全部为空，无法发起训练，请先补充有效销量数据");
        }
        boolean hasNonZeroTarget = rows.stream()
                .map(ModelTrainFeatureDataTb::getGasSales)
                .filter(value -> value != null)
                .anyMatch(value -> value.compareTo(BigDecimal.ZERO) != 0);
        if (!hasNonZeroTarget) {
            throw new BusinessException("训练目标销量全部为0，无法发起训练，请先检查销量数据质量");
        }
    }

    /** 校验已保存的训练请求快照，防止失败重试绕过当前质量规则。 */
    public void validatePayload(List<JsonNode> dataset) {
        if (dataset == null || dataset.isEmpty()) {
            throw new BusinessException("训练时间范围和模型作用范围内没有可用训练数据");
        }
        List<JsonNode> targets = dataset.stream().map(row -> row.get("gas_sales")).filter(value -> value != null && !value.isNull()).toList();
        if (targets.isEmpty()) {
            throw new BusinessException("训练目标销量全部为空，无法发起训练，请先补充有效销量数据");
        }
        if (targets.stream().noneMatch(value -> value.isNumber() && value.decimalValue().compareTo(BigDecimal.ZERO) != 0)) {
            throw new BusinessException("训练目标销量全部为0，无法发起训练，请先检查销量数据质量");
        }
    }
}
