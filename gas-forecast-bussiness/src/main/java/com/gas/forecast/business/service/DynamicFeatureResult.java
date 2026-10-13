package com.gas.forecast.business.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** 当前训练批次生成的动态特征值和可追溯快照。 */
public record DynamicFeatureResult(Map<String, Map<String, BigDecimal>> valuesByDate, ObjectNode snapshot) {

    public void appendValues(ObjectNode row, String date) {
        Map<String, BigDecimal> values = valuesByDate.get(date);
        if (values != null) {
            values.forEach(row::put);
        }
    }

    public Set<String> featureCodes() {
        Set<String> codes = new LinkedHashSet<>();
        valuesByDate.values().forEach(values -> codes.addAll(values.keySet()));
        return Set.copyOf(codes);
    }

    public int valueCount() {
        return valuesByDate.values().stream().mapToInt(Map::size).sum();
    }
}
