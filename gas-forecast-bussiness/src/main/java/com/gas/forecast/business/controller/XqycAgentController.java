package com.gas.forecast.business.controller;

import com.gas.forecast.business.service.XqycForecastPersistenceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@CrossOrigin
@RestController
public class XqycAgentController {

    private static final Map<String, AgentMeta> AGENTS = new LinkedHashMap<>();

    static {
        AGENTS.put("winter-supply", new AgentMeta("winter-supply", "冬季保供预测智能体", "GradientBoosting / SeasonalMean3Y", 15));
        AGENTS.put("monthly-sales", new AgentMeta("monthly-sales", "月度销量预测智能体", "Prophet + AR残差校正", 12));
        AGENTS.put("short-term", new AgentMeta("short-term", "短期客户预测智能体", "Prophet + LightGBM", 14));
    }

    private final ObjectMapper objectMapper;
    private final XqycForecastPersistenceService persistenceService;
    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    public XqycAgentController(ObjectMapper objectMapper, XqycForecastPersistenceService persistenceService) {
        this.objectMapper = objectMapper;
        this.persistenceService = persistenceService;
    }

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

    @GetMapping("/monthly-results")
    public JsonNode listMonthlyResults() throws Exception {
        return listResultFiles("xqyc/result_data/*.json", true);
    }

    @GetMapping("/monthly-results/detail")
    public ResponseEntity<JsonNode> getMonthlyResult(@RequestParam String province,
                                                     @RequestParam String industry) throws Exception {
        return readResult("xqyc/result_data/" + province + "_" + industry + ".json", industry);
    }

    @GetMapping("/short-term-results")
    public JsonNode listShortTermResults() throws Exception {
        ObjectNode root = (ObjectNode) listResultFiles("xqyc/short_term_data/*.json", false);
        ArrayNode results = (ArrayNode) root.get("results");
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
        return root;
    }

    @GetMapping("/short-term-results/detail")
    public ResponseEntity<JsonNode> getShortTermResult(@RequestParam String province,
                                                       @RequestParam String industry) throws Exception {
        return readResult("xqyc/short_term_data/" + province + "_" + industry + ".json", industry);
    }

    @GetMapping("/short-term-results/customer-detail")
    public ResponseEntity<JsonNode> getShortTermCustomerResult(@RequestParam String province,
                                                              @RequestParam String industry,
                                                              @RequestParam String customer) throws Exception {
        return readResult("xqyc/short_term_data/" + province + "_" + industry + "_" + customer + ".json", industry);
    }

    @GetMapping("/winter-supply-results")
    public JsonNode listWinterSupplyResults() throws Exception {
        return listResultFiles("xqyc/winter_supply_data/*.json", false);
    }

    @GetMapping("/winter-supply-results/detail")
    public ResponseEntity<JsonNode> getWinterSupplyResult(@RequestParam String province) throws Exception {
        return readResult("xqyc/winter_supply_data/" + province + ".json", null);
    }

    @PostMapping(value = "/predict", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public StreamingResponseBody predict(@RequestBody JsonNode request) {
        String agentId = request.path("agent_id").asText("winter-supply");
        String agentName = AGENTS.getOrDefault(agentId, AGENTS.get("winter-supply")).name();
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

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public StreamingResponseBody chat(@RequestBody JsonNode request) {
        String agentId = request.path("agent_id").asText("winter-supply");
        String text = "已基于 " + AGENTS.getOrDefault(agentId, AGENTS.get("winter-supply")).name()
                + " 的预测上下文生成分析。当前 Java 后端返回的是迁移后的本地结果数据。";
        return outputStream -> {
            writeEvent(outputStream, objectMapper.createObjectNode().put("type", "stream").put("content", text));
            writeEvent(outputStream, objectMapper.createObjectNode().put("type", "complete").put("content", text));
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
        outputStream.write(("data: " + objectMapper.writeValueAsString(event) + "\n\n").getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }

    private record AgentMeta(String id, String name, String model, int defaultForecastDays) {
    }

    private record FileParts(String industry, String customer) {
    }
}
