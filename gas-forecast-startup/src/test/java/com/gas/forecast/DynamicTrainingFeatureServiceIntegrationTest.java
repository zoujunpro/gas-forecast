package com.gas.forecast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gas.forecast.business.service.DynamicFeatureResult;
import com.gas.forecast.business.service.DynamicTrainingFeatureService;
import com.gas.forecast.dao.domain.ModelTrainConfigTb;
import com.gas.forecast.dao.domain.ModelTrainFeatureDataTb;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 动态特征独立集成测试。
 *
 * <p>
 * 本测试直接调用 {@link DynamicTrainingFeatureService}，不会进入模型训练服务，
 * 不会创建训练批次、提交模型平台或写入训练记录。
 * </p>
 *
 * <p>
 * 测试会真实访问钢联研报、钢联指标和当前配置的大模型接口， 因此默认关闭，只有显式指定
 * {@code -Dgas.mysteel.dynamic-test.enabled=true} 才会运行。
 * </p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {"spring.config.import=optional:file:../.env[.properties]", "gas.mysteel.dynamic-features-enabled=true",
        "gas.mysteel.dynamic-features-required=true"})
class DynamicTrainingFeatureServiceIntegrationTest {

    @Autowired
    private DynamicTrainingFeatureService dynamicTrainingFeatureService;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @EnabledIfSystemProperty(named = "gas.mysteel.dynamic-test.enabled", matches = "true")
    void prepareJiangsuPowerDynamicFeaturesWithoutTraining() throws Exception {
        ModelTrainConfigTb config = buildTrainConfig();
        List<ModelTrainFeatureDataTb> baseRows = buildMonthlyBaseRows(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 9, 1));

        // 仅获取动态特征，不调用 ModelTrainExecutionService 和模型训练平台。
        DynamicFeatureResult result = dynamicTrainingFeatureService.prepare(config, baseRows);

        assertEquals("SUCCESS", result.snapshot().path("status").asText());
        assertFalse(result.snapshot().path("indicator_definitions").isEmpty(), "应当提取到动态指标定义");
        assertFalse(result.snapshot().path("selected_indexes").isEmpty(), "应当选择到可用钢联指标");
        assertFalse(result.valuesByDate().isEmpty(), "应当获取到动态指标历史值");

        // 控制台只输出摘要；完整输入和返回可通过 DynamicTrainingFeatureService 的 DEBUG 日志查看。
        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("status", result.snapshot().path("status").asText());
        summary.put("definition_count", result.snapshot().path("indicator_definitions").size());
        summary.put("selected_count", result.snapshot().path("selected_indexes").size());
        summary.put("excluded_count", result.snapshot().path("excluded_indexes").size());
        summary.put("value_dates", result.valuesByDate().size());
        summary.set("selected_indexes", result.snapshot().path("selected_indexes"));
        summary.set("excluded_indexes", result.snapshot().path("excluded_indexes"));
        System.out.println("DYNAMIC_FEATURE_TEST_RESULT=" + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(summary));
    }

    private ModelTrainConfigTb buildTrainConfig() {
        ModelTrainConfigTb config = new ModelTrainConfigTb();
        config.setTrainCode("JIANGSU_POWER_DYNAMIC_TEST");
        config.setTrainName("江苏电力动态特征独立测试");
        config.setRegionCode("JIANGSU");
        config.setRegionName("江苏");
        config.setIndustryCode("POWER");
        config.setIndustryName("电力");
        config.setCustomerName("全部客户");
        config.setTimeGranularity("MONTH");
        return config;
    }

    private List<ModelTrainFeatureDataTb> buildMonthlyBaseRows(LocalDate begin, LocalDate end) {
        List<ModelTrainFeatureDataTb> rows = new ArrayList<>();
        for (LocalDate date = begin.withDayOfMonth(1); !date.isAfter(end); date = date.plusMonths(1)) {
            ModelTrainFeatureDataTb row = new ModelTrainFeatureDataTb();
            row.setStatDate(date.toString());
            row.setTimeGranularity("MONTH");
            row.setRegionCode("JIANGSU");
            row.setRegionName("江苏");
            row.setIndustryCode("POWER");
            row.setIndustryName("电力");
            rows.add(row);
        }
        return rows;
    }
}
