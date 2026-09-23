package com.gas.forecast.business.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gas.forecast.business.enums.ModelTrainStatus;
import com.gas.forecast.business.enums.ModelForecastStatus;
import com.gas.forecast.business.service.XqycForecastPersistenceService;
import com.gas.forecast.dao.domain.ModelForecastRecordTb;
import com.gas.forecast.dao.domain.ModelForecastResultTb;
import com.gas.forecast.dao.domain.ModelTrainBacktestTb;
import com.gas.forecast.dao.domain.ModelTrainRecordTb;
import com.gas.forecast.dao.mapper.ModelForecastRecordTbMapper;
import com.gas.forecast.dao.mapper.ModelForecastResultTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainBacktestTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainRecordTbMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * 预测智能体兼容接口。
 *
 * <p>为预测智能体前端及流式调用提供配置、训练结果、预测和对话能力。</p>
 */
@CrossOrigin
@RestController
@RequiredArgsConstructor
public class XqycAgentController {

    private static final Map<String, AgentMeta> AGENTS = new LinkedHashMap<>();

    static {
        AGENTS.put(
                "winter-supply", new AgentMeta("winter-supply", "冬季保供预测智能体", "GradientBoosting / SeasonalMean3Y", 15));
        AGENTS.put("monthly-sales", new AgentMeta("monthly-sales", "月度销量预测智能体", "Prophet + AR残差校正", 12));
        AGENTS.put("short-term", new AgentMeta("short-term", "短期客户预测智能体", "Prophet + LightGBM", 14));
    }

    private final ObjectMapper objectMapper;
    private final XqycForecastPersistenceService persistenceService;
    private final ModelTrainRecordTbMapper trainRecordMapper;
    private final ModelTrainBacktestTbMapper trainBacktestMapper;
    private final ModelForecastRecordTbMapper forecastRecordMapper;
    private final ModelForecastResultTbMapper forecastResultMapper;
    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    /**
     * 查询指定智能体配置。
     *
     * @param agentId 智能体编码
     * @return 智能体配置信息，不存在时返回 404
     */
    @GetMapping("/agents/config")
    public ResponseEntity<JsonNode> getAgentConfig(@RequestParam String agentId) {
        AgentMeta meta = AGENTS.get(agentId);
        if (meta == null) {
            return ResponseEntity.notFound().build();
        }
        ObjectNode config = objectMapper.createObjectNode();
        config.put("id", meta.id());
        config.put("name", meta.name());
        config.put("model", meta.model());
        config.put("default_forecast_days", meta.defaultForecastDays());
        config.put("data_source", "gas_data");
        config.put("backend", "java");
        return ResponseEntity.ok(config);
    }

    /**
     * 查询月度销量训练结果列表。
     *
     * @return 月度销量训练结果
     * @throws Exception 结果文件读取失败时抛出
     */
    @GetMapping("/monthly-results")
    public JsonNode listMonthlyResults() throws Exception {
        return listResultFiles("xqyc/result_data/*.json", true);
    }

    /**
     * 查询月度销量训练结果详情。
     *
     * @param province 省份名称
     * @param industry 行业名称
     * @return 月度销量训练结果详情，不存在时返回 404
     * @throws Exception 结果文件读取失败时抛出
     */
    @GetMapping("/monthly-results/detail")
    public ResponseEntity<JsonNode> getMonthlyResult(@RequestParam String province, @RequestParam String industry)
            throws Exception {
        return readResult("xqyc/result_data/" + province + "_" + industry + ".json", industry);
    }

    /**
     * 查询短期客户训练结果列表。
     *
     * @return 短期客户训练结果
     */
    @GetMapping("/short-term-results")
    public JsonNode listShortTermResults() {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode results = objectMapper.createArrayNode();
        List<ModelTrainRecordTb> records = trainRecordMapper.selectList(Wrappers.<ModelTrainRecordTb>lambdaQuery()
                .eq(ModelTrainRecordTb::getAgentCode, "short-term")
                .eq(ModelTrainRecordTb::getStatus, ModelTrainStatus.SUCCESS.getCode())
                .orderByDesc(ModelTrainRecordTb::getUpdatedAt)
                .orderByDesc(ModelTrainRecordTb::getId));
        Set<String> seenScopes = new LinkedHashSet<>();
        for (ModelTrainRecordTb record : records) {
            String scopeKey = trainScopeKey(record);
            if (seenScopes.add(scopeKey)) results.add(toShortTermResult(record, false));
        }
        Map<String, Set<String>> customersByScope = new LinkedHashMap<>();

        for (JsonNode node : results) {
            ObjectNode item = (ObjectNode) node;
            String customer = item.path("customer").asText("");
            if (!customer.isBlank()) {
                customersByScope
                        .computeIfAbsent(shortTermScopeKey(item), key -> new LinkedHashSet<>())
                        .add(customer);
            }
        }

        for (JsonNode node : results) {
            ObjectNode item = (ObjectNode) node;
            Set<String> scopedCustomers = customersByScope.get(shortTermScopeKey(item));
            if (scopedCustomers != null && !scopedCustomers.isEmpty() && !item.has("customer")) {
                ArrayNode customerNodes = objectMapper.createArrayNode();
                for (String customer : scopedCustomers) {
                    customerNodes.add(customer);
                }
                item.set("customers", customerNodes);
            }
        }
        root.set("results", results);
        return root;
    }

    /**
     * 查询短期客户训练结果详情。
     *
     * @param province 省份名称
     * @param industry 行业名称
     * @return 短期客户训练结果详情，不存在时返回 404
     */
    @GetMapping("/short-term-results/detail")
    public ResponseEntity<JsonNode> getShortTermResult(@RequestParam String province, @RequestParam String industry) {
        ModelTrainRecordTb record = latestShortTermRecord(province, industry, null);
        return record == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(toShortTermResult(record, true));
    }

    /**
     * 查询指定客户的短期训练结果详情。
     *
     * @param province 省份名称
     * @param industry 行业名称
     * @param customer 客户名称
     * @return 客户短期训练结果详情，不存在时返回 404
     */
    @GetMapping("/short-term-results/customer-detail")
    public ResponseEntity<JsonNode> getShortTermCustomerResult(
            @RequestParam String province, @RequestParam String industry, @RequestParam String customer) {
        ModelTrainRecordTb record = latestShortTermRecord(province, industry, customer);
        return record == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(toShortTermResult(record, true));
    }

    private ModelTrainRecordTb latestShortTermRecord(String province, String industry, String customer) {
        var query = Wrappers.<ModelTrainRecordTb>lambdaQuery()
                .eq(ModelTrainRecordTb::getAgentCode, "short-term")
                .eq(ModelTrainRecordTb::getStatus, ModelTrainStatus.SUCCESS.getCode())
                .eq(ModelTrainRecordTb::getRegionName, province)
                .eq(ModelTrainRecordTb::getIndustryName, industry);
        if (customer == null || customer.isBlank())
            query.and(q -> q.isNull(ModelTrainRecordTb::getCustomerName)
                    .or()
                    .eq(ModelTrainRecordTb::getCustomerName, "")
                    .or()
                    .eq(ModelTrainRecordTb::getCustomerName, "ALL")
                    .or()
                    .likeRight(ModelTrainRecordTb::getCustomerName, "全部"));
        else query.eq(ModelTrainRecordTb::getCustomerName, customer);
        return trainRecordMapper.selectOne(query.orderByDesc(ModelTrainRecordTb::getUpdatedAt)
                .orderByDesc(ModelTrainRecordTb::getId)
                .last("limit 1"));
    }

    private ObjectNode toShortTermResult(ModelTrainRecordTb record, boolean includeBacktest) {
        ObjectNode item = objectMapper.createObjectNode();
        item.put("province", displayScope(record.getRegionName(), "全部区域"));
        item.put("industry", displayScope(record.getIndustryName(), "全部行业"));
        if (!isAllScope(record.getCustomerName())) {
            item.put("customer", record.getCustomerName());
        }
        item.put("batch_no", record.getBatchNo());
        item.put("model_name", displayScope(record.getBestModel(), "暂无推荐模型"));
        item.put("model_version", displayScope(record.getModelVersion(), "-"));
        ObjectNode metrics = objectMapper.createObjectNode();
        putDecimal(metrics, "mape", record.getMape());
        putDecimal(metrics, "wmape", record.getWmape());
        putDecimal(metrics, "smape", record.getSmape());
        putDecimal(metrics, "rmse", record.getRmse());
        putDecimal(metrics, "mae", record.getMae());
        putDecimal(metrics, "r2", record.getR2());
        item.set("metrics", metrics);
        if (includeBacktest) {
            ArrayNode dates = objectMapper.createArrayNode();
            ArrayNode actual = objectMapper.createArrayNode();
            ArrayNode predicted = objectMapper.createArrayNode();
            List<ModelTrainBacktestTb> rows =
                    trainBacktestMapper.selectList(Wrappers.<ModelTrainBacktestTb>lambdaQuery()
                            .eq(ModelTrainBacktestTb::getTrainBatchNo, record.getBatchNo())
                            .orderByAsc(ModelTrainBacktestTb::getTrainDate)
                            .orderByAsc(ModelTrainBacktestTb::getId));
            DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
            for (ModelTrainBacktestTb row : rows) {
                dates.add(row.getTrainDate()
                        .toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                        .format(formatter));
                actual.add(row.getActualValue());
                predicted.add(row.getPredictedValue());
            }
            item.set("dates", dates);
            item.set("actual", actual);
            item.set("predicted", predicted);
            item.set("history_dates", dates.deepCopy());
            item.set("history_values", actual.deepCopy());
            appendLatestForecast(item, record);
        }
        return item;
    }

    private void appendLatestForecast(ObjectNode item, ModelTrainRecordTb trainRecord) {
        var query = Wrappers.<ModelForecastRecordTb>lambdaQuery()
                .eq(ModelForecastRecordTb::getAgentCode, "short-term")
                .eq(ModelForecastRecordTb::getStatus, ModelForecastStatus.SUCCESS.getCode());
        if (!isAllScope(trainRecord.getRegionCode())) {
            query.eq(ModelForecastRecordTb::getRegionCode, trainRecord.getRegionCode());
        }
        if (isAllScope(trainRecord.getIndustryCode())) {
            query.and(q -> q.isNull(ModelForecastRecordTb::getIndustryCode)
                    .or()
                    .eq(ModelForecastRecordTb::getIndustryCode, "")
                    .or()
                    .eq(ModelForecastRecordTb::getIndustryCode, "ALL"));
        } else {
            query.eq(ModelForecastRecordTb::getIndustryCode, trainRecord.getIndustryCode());
        }
        if (isAllScope(trainRecord.getCustomerName())) {
            query.and(q -> q.isNull(ModelForecastRecordTb::getCustomerCode)
                    .or()
                    .eq(ModelForecastRecordTb::getCustomerCode, "")
                    .or()
                    .eq(ModelForecastRecordTb::getCustomerCode, "ALL"));
        } else {
            query.eq(ModelForecastRecordTb::getCustomerCode, trainRecord.getCustomerCode());
        }
        ModelForecastRecordTb forecastRecord =
                forecastRecordMapper.selectOne(query.orderByDesc(ModelForecastRecordTb::getForecastEndTime)
                        .orderByDesc(ModelForecastRecordTb::getId)
                        .last("limit 1"));
        if (forecastRecord == null) return;

        List<ModelForecastResultTb> points =
                forecastResultMapper.selectList(Wrappers.<ModelForecastResultTb>lambdaQuery()
                        .eq(ModelForecastResultTb::getForecastBatchNo, forecastRecord.getForecastBatchNo())
                        .orderByAsc(ModelForecastResultTb::getForecastDate)
                        .orderByAsc(ModelForecastResultTb::getId));
        if (points.isEmpty()) return;
        ArrayNode futureDates = objectMapper.createArrayNode();
        ArrayNode futurePredicted = objectMapper.createArrayNode();
        for (ModelForecastResultTb point : points) {
            futureDates.add(point.getForecastDate());
            futurePredicted.add(point.getForecastValue());
        }
        item.put("forecast_batch_no", forecastRecord.getForecastBatchNo());
        item.set("future_dates", futureDates);
        item.set("future_predicted", futurePredicted);
    }

    private void putDecimal(ObjectNode target, String field, java.math.BigDecimal value) {
        if (value == null) target.putNull(field);
        else target.put(field, value);
    }

    private String trainScopeKey(ModelTrainRecordTb record) {
        return displayScope(record.getRegionName(), "全部区域") + "\t"
                + displayScope(record.getIndustryName(), "全部行业") + "\t"
                + displayScope(record.getCustomerName(), "");
    }

    private String displayScope(String value, String fallback) {
        return isAllScope(value) ? fallback : value;
    }

    private boolean isAllScope(String value) {
        return value == null || value.isBlank() || "ALL".equalsIgnoreCase(value) || value.startsWith("全部");
    }

    /**
     * 查询冬季保供训练结果列表。
     *
     * @return 冬季保供训练结果
     * @throws Exception 结果文件读取失败时抛出
     */
    @GetMapping("/winter-supply-results")
    public JsonNode listWinterSupplyResults() throws Exception {
        return listResultFiles("xqyc/winter_supply_data/*.json", false);
    }

    /**
     * 查询指定省份的冬季保供训练结果详情。
     *
     * @param province 省份名称
     * @return 冬季保供训练结果详情，不存在时返回 404
     * @throws Exception 结果文件读取失败时抛出
     */
    @GetMapping("/winter-supply-results/detail")
    public ResponseEntity<JsonNode> getWinterSupplyResult(@RequestParam String province) throws Exception {
        return readResult("xqyc/winter_supply_data/" + province + ".json", null);
    }

    /**
     * 执行流式预测。
     *
     * @param request 预测请求参数
     * @return 流式预测响应
     */
    @PostMapping(value = "/predict", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public StreamingResponseBody predict(@RequestBody JsonNode request) {
        String agentId = request.path("agent_id").asText("winter-supply");
        String agentName =
                AGENTS.getOrDefault(agentId, AGENTS.get("winter-supply")).name();
        return outputStream -> {
            writeEvent(outputStream, event("progress", "load_data", "加载历史数据", null, null));
            writeEvent(outputStream, event("progress", "preprocess", "数据预处理与特征工程", null, null));
            writeEvent(outputStream, event("progress", "train_model", "训练预测模型", null, null));
            writeEvent(outputStream, event("progress", "predict", "预测结果入库", null, null));
            XqycForecastPersistenceService.PersistSummary persistSummary;
            try {
                persistSummary = persistenceService.persist(agentId, request);
            } catch (Exception e) {
                writeEvent(outputStream, event("error", "predict", "预测结果入库失败: " + e.getMessage(), null, null));
                return;
            }
            writeEvent(outputStream, event("progress", "analyze", "生成分析报告", null, null));
            ObjectNode data = objectMapper.createObjectNode();
            data.put("agent_id", agentId);
            data.put("agent_name", agentName);
            data.put("batch_count", persistSummary.batchCount());
            data.put("result_count", persistSummary.resultCount());
            data.put("total_volume", persistSummary.resultCount());
            data.put("summary", "预测结果已写入 model_forecast_result_tb。");
            writeEvent(outputStream, event("result", null, null, data, agentName + "运行完成。"));
        };
    }

    /**
     * 执行智能体流式对话。
     *
     * @param request 对话请求参数
     * @return 流式对话响应
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public StreamingResponseBody chat(@RequestBody JsonNode request) {
        String agentId = request.path("agent_id").asText("winter-supply");
        String text = "已基于 "
                + AGENTS.getOrDefault(agentId, AGENTS.get("winter-supply")).name()
                + " 的预测上下文生成分析。当前 Java 后端返回的是迁移后的本地结果数据。";
        return outputStream -> {
            writeEvent(
                    outputStream,
                    objectMapper.createObjectNode().put("type", "stream").put("content", text));
            writeEvent(
                    outputStream,
                    objectMapper.createObjectNode().put("type", "complete").put("content", text));
        };
    }

    private JsonNode listResultFiles(String pattern, boolean deriveIndustry) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode results = objectMapper.createArrayNode();
        for (Resource resource : resolver.getResources("classpath*:" + pattern)) {
            JsonNode json = readResource(resource);
            ObjectNode item = json.deepCopy();
            FileParts parts = parseName(resource.getFilename());
            if (!item.has("industry") && (deriveIndustry || parts.industry() != null)) {
                item.put("industry", parts.industry());
            }
            if (parts.customer() != null) {
                item.put("customer", parts.customer());
            }
            results.add(item);
        }
        root.set("results", results);
        return root;
    }

    private ResponseEntity<JsonNode> readResult(String path, String industry) throws Exception {
        Resource resource = resolver.getResource("classpath:" + path);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        ObjectNode json = readResource(resource).deepCopy();
        if (industry != null && !json.has("industry")) {
            json.put("industry", industry);
        }
        return ResponseEntity.ok(json);
    }

    private JsonNode readResource(Resource resource) throws Exception {
        try (InputStream inputStream = resource.getInputStream()) {
            return objectMapper.readTree(inputStream);
        }
    }

    private FileParts parseName(String filename) {
        String base = filename == null ? "" : filename.replaceFirst("\\.json$", "");
        String[] parts = base.split("_", 3);
        String industry = parts.length >= 2 ? parts[1] : null;
        String customer = parts.length >= 3 ? parts[2] : null;
        return new FileParts(industry, customer);
    }

    private String shortTermScopeKey(JsonNode item) {
        return item.path("province").asText("") + "\t" + item.path("industry").asText("");
    }

    private ObjectNode event(String type, String node, String message, JsonNode data, String content) {
        ObjectNode event = objectMapper.createObjectNode();
        event.put("type", type);
        if (node != null) {
            event.put("node", node);
        }
        if (message != null) {
            event.put("message", message);
        }
        if (data != null) {
            event.set("data", data);
        }
        if (content != null) {
            event.put("content", content);
        }
        return event;
    }

    private void writeEvent(java.io.OutputStream outputStream, JsonNode event) throws java.io.IOException {
        outputStream.write(
                ("data: " + objectMapper.writeValueAsString(event) + "\n\n").getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class AgentMeta {
        private String id;

        private String name;

        private String model;

        private int defaultForecastDays;

        public String id() {
            return id;
        }

        public String name() {
            return name;
        }

        public String model() {
            return model;
        }

        public int defaultForecastDays() {
            return defaultForecastDays;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class FileParts {
        private String industry;

        private String customer;

        public String industry() {
            return industry;
        }

        public String customer() {
            return customer;
        }
    }
}
