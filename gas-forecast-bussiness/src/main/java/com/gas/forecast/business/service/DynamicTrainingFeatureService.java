package com.gas.forecast.business.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gas.forecast.business.component.ForecastAnalysisLlmClient;
import com.gas.forecast.business.component.MysteelIndexClient;
import com.gas.forecast.business.component.MysteelMarketAnalysisClient;
import com.gas.forecast.business.component.dto.MysteelIndexCandidate;
import com.gas.forecast.business.component.dto.MysteelIndexValue;
import com.gas.forecast.business.skill.SkillPromptLoader;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.ModelTrainConfigTb;
import com.gas.forecast.dao.domain.ModelTrainFeatureDataTb;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 训练期动态特征编排服务。
 *
 * <p>
 * 调用时机：基础训练数据加载完成之后、训练请求提交到模型平台之前。
 * </p>
 * <p>
 * 固定流程：获取研报 -> 提取指标定义 -> 搜索钢联候选指标 -> 大模型选择指标 -> 查询历史值 -> 按训练粒度聚合 -> 质量过滤 ->
 * 生成动态特征及训练快照。
 * </p>
 */
@Service
@Slf4j
public class DynamicTrainingFeatureService {

    private final ObjectMapper objectMapper;
    private final MysteelMarketAnalysisClient reportClient;
    private final MysteelIndexClient indexClient;
    private final ForecastAnalysisLlmClient llmClient;
    private final SkillPromptLoader promptLoader;
    private final MysteelIndexSelectionValidator selectionValidator;
    private final ModelTrainStepRecorder stepRecorder;

    @Value("${gas.mysteel.dynamic-features-enabled:true}")
    private boolean enabled;
    @Value("${gas.mysteel.dynamic-features-required:false}")
    private boolean required;
    @Value("${gas.mysteel.dynamic-max-features:10}")
    private int maxFeatures;
    @Value("${gas.mysteel.dynamic-min-coverage:0.7}")
    private double minCoverage;

    public DynamicTrainingFeatureService(ObjectMapper objectMapper, MysteelMarketAnalysisClient reportClient, MysteelIndexClient indexClient, ForecastAnalysisLlmClient llmClient,
            SkillPromptLoader promptLoader, MysteelIndexSelectionValidator selectionValidator, ModelTrainStepRecorder stepRecorder) {
        this.objectMapper = objectMapper;
        this.reportClient = reportClient;
        this.indexClient = indexClient;
        this.llmClient = llmClient;
        this.promptLoader = promptLoader;
        this.selectionValidator = selectionValidator;
        this.stepRecorder = stepRecorder;
    }
    /**
     * 动态特征获取总入口，由模型训练服务调用。
     *
     * @param config
     *            本次训练配置，提供区域、行业、客户和时间粒度等业务范围
     * @param baseRows
     *            已提前抽取的基础训练数据，用于确定训练日期范围并对齐动态特征值
     * @return 本次训练可使用的动态特征值，以及用于重试和审计的动态特征快照
     */
    public DynamicFeatureResult prepare(ModelTrainConfigTb config, List<ModelTrainFeatureDataTb> baseRows) {
        return prepare(config, baseRows, null, 1, null);
    }

    public DynamicFeatureResult prepare(ModelTrainConfigTb config, List<ModelTrainFeatureDataTb> baseRows, String batchNo, int attemptNo, Long parentStepId) {
        long totalStartedAt = System.nanoTime();
        Long[] activeStep = new Long[1];
        log.info("[动态特征-开始] trainCode={}, region={}, industry={}, customer={}, granularity={}, baseRows={}", config.getTrainCode(), config.getRegionName(), config.getIndustryName(),
                config.getCustomerName(), config.getTimeGranularity(), baseRows.size());
        log.info("[动态特征-开始-配置] config={}", config);
        // 步骤0：动态特征开关关闭时直接返回，不影响原有基础特征训练流程。
        if (!enabled) {
            log.info("[动态特征-跳过] trainCode={}, reason=dynamic-features-enabled=false", config.getTrainCode());
            return empty("DISABLED", null);
        }
        try {
            DynamicFeatureResult result = prepareEnabled(config, baseRows, batchNo, attemptNo, parentStepId, activeStep);
            log.info("[动态特征-结束] trainCode={}, status={}, totalElapsedMs={}", config.getTrainCode(), result.snapshot().path("status").asText(), elapsedMs(totalStartedAt));
            return result;
        } catch (RuntimeException exception) {
            if (activeStep[0] != null)
                stepRecorder.fail(activeStep[0], exception);
            log.error("[动态特征-失败] trainCode={}, required={}, totalElapsedMs={}, error={}", config.getTrainCode(), required, elapsedMs(totalStartedAt), exception.getMessage(), exception);
            // required=true：动态特征失败即终止训练；false：降级为仅使用基础特征训练。
            if (required) {
                throw exception;
            }
            return empty("FAILED", exception.getMessage());
        }
    }

    private DynamicFeatureResult prepareEnabled(ModelTrainConfigTb config, List<ModelTrainFeatureDataTb> baseRows, String batchNo, int attemptNo, Long parentStepId, Long[] activeStep) {
        // 步骤1：从基础训练数据计算本次训练的实际起止日期，而不是直接使用配置日期。
        LocalDate begin = baseRows.stream().map(row -> parseDate(row.getStatDate())).min(LocalDate::compareTo).orElseThrow(() -> new BusinessException("训练数据缺少有效日期"));
        LocalDate end = baseRows.stream().map(row -> parseDate(row.getStatDate())).max(LocalDate::compareTo).orElse(begin);
        log.info("[动态特征-步骤1] 训练日期范围 begin={}, end={}", begin, end);

        // 步骤2：根据区域、行业和训练时间范围组织问题，并从钢联获取市场分析报告。
        String reportQuery = buildReportQuery(config, begin, end);
        activeStep[0] = startChild(batchNo, attemptNo, parentStepId, "DYNAMIC_INDICATOR_DISCOVERY", "获取动态指标", 1, "通过钢联市场研报和大模型提取指标；查询条件=" + reportQuery);
        log.info("[动态特征-步骤2-入参] 钢联研报查询={}", reportQuery);
        long reportStartedAt = System.nanoTime();
        String report = reportClient.analyze(reportQuery);
        log.info("[动态特征-步骤2-结果] 钢联研报获取成功，字符数={}, elapsedMs={}", report.length(), elapsedMs(reportStartedAt));
        log.info("[动态特征-步骤2-完整结果] report={}", report);

        // 步骤3：将报告交给大模型，从报告事实中提取结构化的动态指标定义。
        ArrayNode definitions = extractDefinitions(report, config, begin, end);
        ObjectNode discoveryResult = objectMapper.createObjectNode().put("method", "钢联市场研报 + 大模型提取").put("report_query", reportQuery).put("report_length", report.length());
        discoveryResult.set("indicator_definitions", definitions);
        finishChild(activeStep, discoveryResult.toString(), definitions.size());
        log.info("[动态特征-步骤3-结果] 大模型提取指标定义数量={}", definitions.size());
        log.info("[动态特征-步骤3-完整结果] indicatorDefinitions={}", definitions);

        // 步骤4：初始化本次训练快照。快照记录原始报告、指标定义、入选项和排除原因。
        ObjectNode snapshot = objectMapper.createObjectNode().put("status", "SUCCESS").put("report_query", reportQuery).put("report", report);
        snapshot.set("indicator_definitions", definitions);
        ArrayNode selectedSnapshot = snapshot.putArray("selected_indexes");
        ArrayNode excludedSnapshot = snapshot.putArray("excluded_indexes");
        ArrayNode candidateSnapshot = snapshot.putArray("candidate_groups");

        // 步骤5：先查询全部指标定义的钢联候选项，不在循环内调用大模型。
        List<CandidateGroup> candidateGroups = new ArrayList<>();
        activeStep[0] = startChild(batchNo, attemptNo, parentStepId, "DYNAMIC_CANDIDATE_QUERY", "查询指标信息", 2, "指标定义=" + definitions.size());
        int definitionNumber = 1;
        for (JsonNode definition : definitions) {
            String definitionId = String.format("definition_%03d", definitionNumber++);
            String searchDescription = definition.path("search_description").asText("").trim();
            String indicatorName = definition.path("indicator_name").asText("");
            log.info("[动态特征-步骤5] 开始查询指标 definitionId={}, definition={}, definitionContent={}, searchDescription={}", definitionId, indicatorName, definition, searchDescription);
            if (!TextUtils.hasText(searchDescription)) {
                log.warn("[动态特征-步骤5-排除] definitionId={}, definition={}, reason=缺少search_description", definitionId, indicatorName);
                exclude(excludedSnapshot, definition, null, "缺少search_description");
                continue;
            }

            log.info("[动态特征-步骤5-查询入参] definitionId={}, query={}, limit={}", definitionId, searchDescription, 20);
            long searchStartedAt = System.nanoTime();
            List<MysteelIndexCandidate> candidates = indexClient.searchIndexes(searchDescription, 20);
            log.info("[动态特征-步骤5-查询结果] definitionId={}, definition={}, candidates={}, elapsedMs={}", definitionId, indicatorName, candidates.size(), elapsedMs(searchStartedAt));
            log.info("[动态特征-步骤5-完整结果] definitionId={}, definition={}, candidateList={}", definitionId, indicatorName, candidates);
            if (candidates.isEmpty()) {
                log.warn("[动态特征-步骤5-排除] definitionId={}, definition={}, reason=未搜索到钢联候选指标", definitionId, indicatorName);
                exclude(excludedSnapshot, definition, null, "未搜索到钢联候选指标");
                continue;
            }
            candidateGroups.add(new CandidateGroup(definitionId, definition, candidates));
            ObjectNode groupSnapshot = candidateSnapshot.addObject().put("definition_id", definitionId);
            groupSnapshot.set("indicator_definition", definition);
            groupSnapshot.set("candidates", objectMapper.valueToTree(candidates));
        }
        finishChild(activeStep, candidateSnapshot.toString(), candidateGroups.size());

        // 步骤6：把全部指标定义及其候选项一次性交给大模型，统一比较并选择。
        activeStep[0] = startChild(batchNo, attemptNo, parentStepId, "DYNAMIC_INDICATOR_CONFIRM", "确认最终指标", 3, "候选分组=" + candidateGroups.size());
        List<SelectedCandidate> selectedCandidates = selectIndexes(candidateGroups);
        Set<String> selectedDefinitionIds = new HashSet<>();
        selectedCandidates.forEach(item -> selectedDefinitionIds.add(item.group().definitionId()));
        for (CandidateGroup group : candidateGroups) {
            if (!selectedDefinitionIds.contains(group.definitionId())) {
                log.warn("[动态特征-步骤6-排除] definitionId={}, definition={}, reason=大模型未选择合适指标", group.definitionId(), group.definition().path("indicator_name").asText(""));
                exclude(excludedSnapshot, group.definition(), null, "大模型未选择合适指标");
            }
        }
        log.info("[动态特征-步骤6-统一选择结果] candidateGroups={}, selectedCandidates={}", candidateGroups.size(), selectedCandidates);

        // valuesByDate 最终按基础数据日期保存 dynamic_001、dynamic_002 等运行期特征值。
        Map<String, Map<String, BigDecimal>> valuesByDate = new LinkedHashMap<>();
        Set<String> usedCodes = new HashSet<>();
        int runtimeNumber = 1;
        for (SelectedCandidate selected : selectedCandidates) {
            if (runtimeNumber > Math.max(maxFeatures, 1))
                break;
            JsonNode definition = selected.group().definition();
            MysteelIndexCandidate candidate = selected.candidate();
            String indicatorName = definition.path("indicator_name").asText("");
            if (!usedCodes.add(candidate.indexCode())) {
                log.warn("[动态特征-步骤6-排除] definition={}, indexCode={}, reason=本批次重复", indicatorName, candidate.indexCode());
                exclude(excludedSnapshot, definition, candidate, "指标编码在本批次重复");
                continue;
            }

            // 步骤7：根据统一入选的 indexCode 查询训练日期范围内的钢联历史指标值。
            log.info("[动态特征-步骤7-入参] definitionId={}, indexCode={}, indexName={}, begin={}, end={}", selected.group().definitionId(), candidate.indexCode(), candidate.indexName(), begin, end);
            long valueStartedAt = System.nanoTime();
            List<MysteelIndexValue> sourceValues = indexClient.fetchIndexValues(candidate.indexCode(), begin, end);
            log.info("[动态特征-步骤7-结果] indexCode={}, sourceValues={}, elapsedMs={}", candidate.indexCode(), sourceValues.size(), elapsedMs(valueStartedAt));
            log.info("[动态特征-步骤7-完整结果] indexCode={}, values={}", candidate.indexCode(), sourceValues);

            // 步骤8：将日、周等原始指标值聚合到本次训练的 DAY/TENDAY/MONTH/YEAR 粒度。
            long aggregateStartedAt = System.nanoTime();
            Map<String, BigDecimal> aggregated = aggregate(sourceValues, config.getTimeGranularity(), definition.path("suggested_unit").asText(""));
            log.info("[动态特征-步骤8-结果] indexCode={}, granularity={}, unit={}, aggregatedPeriods={}, elapsedMs={}", candidate.indexCode(), config.getTimeGranularity(),
                    definition.path("suggested_unit").asText(""), aggregated.size(), elapsedMs(aggregateStartedAt));
            log.info("[动态特征-步骤8-完整结果] indexCode={}, aggregatedValues={}", candidate.indexCode(), aggregated);

            // 步骤9：计算指标对基础训练日期的覆盖率，并过滤低覆盖率或常量指标。
            Set<String> baseDates = new HashSet<>();
            baseRows.forEach(row -> baseDates.add(bucket(parseDate(row.getStatDate()), config.getTimeGranularity()).toString()));
            long covered = baseDates.stream().filter(aggregated::containsKey).count();
            double coverage = baseDates.isEmpty() ? 0 : (double) covered / baseDates.size();
            long distinctValues = aggregated.values().stream().distinct().count();
            log.info("[动态特征-步骤9-结果] indexCode={}, basePeriods={}, coveredPeriods={}, coverage={}, distinctValues={}, minCoverage={}", candidate.indexCode(), baseDates.size(), covered,
                    BigDecimal.valueOf(coverage).setScale(4, RoundingMode.HALF_UP), distinctValues, minCoverage);
            if (coverage < minCoverage || distinctValues <= 1) {
                log.warn("[动态特征-步骤9-排除] indexCode={}, reason={}", candidate.indexCode(), coverage < minCoverage ? "数据覆盖率不足" : "指标为常量列");
                exclude(excludedSnapshot, definition, candidate, coverage < minCoverage ? "数据覆盖率不足" : "指标为常量列");
                continue;
            }

            // 步骤10：为通过质量检查的指标分配本批次运行编码，并按日期对齐到训练数据。
            String runtimeCode = String.format("dynamic_%03d", runtimeNumber++);
            for (ModelTrainFeatureDataTb row : baseRows) {
                String date = bucket(parseDate(row.getStatDate()), config.getTimeGranularity()).toString();
                BigDecimal value = aggregated.get(date);
                if (value != null)
                    valuesByDate.computeIfAbsent(row.getStatDate(), ignored -> new LinkedHashMap<>()).put(runtimeCode, value);
            }
            log.info("[动态特征-步骤10-结果] runtimeCode={}, indexCode={}, matchedDates={}", runtimeCode, candidate.indexCode(),
                    valuesByDate.values().stream().filter(values -> values.containsKey(runtimeCode)).count());

            // 步骤11：记录最终入选的指标、钢联编码和覆盖率，供训练重试及问题追溯使用。
            ObjectNode selectedItem = selectedSnapshot.addObject();
            selectedItem.put("runtime_code", runtimeCode).put("definition_id", selected.group().definitionId()).put("indicator_name", indicatorName).put("index_code", candidate.indexCode())
                    .put("index_name", candidate.indexName()).put("coverage", BigDecimal.valueOf(coverage).setScale(4, RoundingMode.HALF_UP));
        }
        ObjectNode confirmationResult = objectMapper.createObjectNode();
        confirmationResult.set("selected_indexes", selectedSnapshot.deepCopy());
        confirmationResult.set("excluded_indexes", excludedSnapshot.deepCopy());
        confirmationResult.put("selected_count", selectedSnapshot.size()).put("excluded_count", excludedSnapshot.size());
        finishChild(activeStep, confirmationResult.toString(), selectedSnapshot.size());

        // 返回给训练服务：valuesByDate 参与本次模型计算，snapshot 随训练请求持久化。
        log.info("[动态特征-完成] trainCode={}, selected={}, excluded={}, valueDates={}", config.getTrainCode(), selectedSnapshot.size(), excludedSnapshot.size(), valuesByDate.size());
        log.info("[动态特征-完整特征值] valuesByDate={}", valuesByDate);
        log.info("[动态特征-完整快照] snapshot={}", snapshot);
        return new DynamicFeatureResult(valuesByDate, snapshot);
    }

    private Long startChild(String batchNo, int attemptNo, Long parentStepId, String code, String name, int order, String input) {
        return TextUtils.hasText(batchNo) && parentStepId != null ? stepRecorder.start(batchNo, attemptNo, parentStepId, 2, code, name, order, input) : null;
    }

    private void finishChild(Long[] activeStep, String output, Integer count) {
        if (activeStep[0] != null)
            stepRecorder.success(activeStep[0], output, count);
        activeStep[0] = null;
    }

    /** 使用指标提取 skill，将研报转换成可继续搜索的结构化指标定义。 */
    private ArrayNode extractDefinitions(String report, ModelTrainConfigTb config, LocalDate begin, LocalDate end) {
        ObjectNode input = objectMapper.createObjectNode().put("report", report).put("prediction_target", "gas_sales").put("region", config.getRegionName()).put("industry", config.getIndustryName())
                .put("customer", config.getCustomerName()).put("begin_date", begin.toString()).put("end_date", end.toString()).put("time_granularity", config.getTimeGranularity());
        String systemPrompt = promptLoader.load("dynamic-indicator-extraction", "indicator-schema.md");
        log.info("[动态特征-步骤3-大模型入参] model={}, temperature={}, systemPrompt={}, userInput={}", llmClient.model(), 0.1, systemPrompt, input);
        long llmStartedAt = System.nanoTime();
        String answer = llmClient.complete(systemPrompt, input.toString(), 0.1);
        log.info("[动态特征-步骤3-大模型返回] model={}, elapsedMs={}, answer={}", llmClient.model(), elapsedMs(llmStartedAt), answer);
        JsonNode parsed = parseJson(answer);
        JsonNode definitions = parsed.path("indicator_definitions");
        if (!definitions.isArray())
            throw new BusinessException("动态指标定义结果格式不正确");
        return (ArrayNode) definitions;
    }

    /** 将所有指标定义及候选项一次性交给指标选择 skill，统一选择并逐组校验。 */
    private List<SelectedCandidate> selectIndexes(List<CandidateGroup> groups) {
        if (groups.isEmpty())
            return List.of();
        ObjectNode input = objectMapper.createObjectNode();
        ArrayNode groupArray = input.putArray("indicator_groups");
        Map<String, CandidateGroup> groupById = new LinkedHashMap<>();
        for (CandidateGroup group : groups) {
            groupById.put(group.definitionId(), group);
            ObjectNode item = groupArray.addObject().put("definition_id", group.definitionId());
            item.set("indicator_definition", group.definition());
            item.set("candidates", objectMapper.valueToTree(group.candidates()));
        }
        input.put("max_features", Math.max(maxFeatures, 1));
        String systemPrompt = promptLoader.load("mysteel-index-selection", "selection-schema.md");
        log.info("[动态特征-步骤6-大模型入参] model={}, temperature={}, groups={}, systemPrompt={}, userInput={}", llmClient.model(), 0.0, groups.size(), systemPrompt, input);
        long llmStartedAt = System.nanoTime();
        String answer = llmClient.complete(systemPrompt, input.toString(), 0.0);
        log.info("[动态特征-步骤6-大模型返回] model={}, elapsedMs={}, answer={}", llmClient.model(), elapsedMs(llmStartedAt), answer);
        JsonNode selections = parseJson(answer).path("selected_indexes");
        if (!selections.isArray())
            throw new BusinessException("钢联指标选择结果格式不正确");
        List<SelectedCandidate> result = new ArrayList<>();
        Set<String> selectedKeys = new HashSet<>();
        for (JsonNode item : selections) {
            String definitionId = item.path("definition_id").asText("").trim();
            String code = item.path("index_code").asText("").trim();
            if (!TextUtils.hasText(definitionId) || !TextUtils.hasText(code))
                continue;
            CandidateGroup group = groupById.get(definitionId);
            if (group == null)
                throw new BusinessException("大模型选择了不存在的指标定义：" + definitionId);
            List<MysteelIndexCandidate> validated = selectionValidator.validate(group.candidates(), List.of(code));
            if (!validated.isEmpty() && selectedKeys.add(definitionId + "\n" + code))
                result.add(new SelectedCandidate(group, validated.get(0)));
        }
        return List.copyOf(result);
    }

    private record CandidateGroup(String definitionId, JsonNode definition, List<MysteelIndexCandidate> candidates) {
    }

    private record SelectedCandidate(CandidateGroup group, MysteelIndexCandidate candidate) {
    }

    /**
     * 将原始指标值聚合到训练粒度：布尔类取最大值，数量类求和，其他类型求平均值。
     */
    private Map<String, BigDecimal> aggregate(List<MysteelIndexValue> values, String granularity, String unit) {
        Map<String, List<BigDecimal>> grouped = new LinkedHashMap<>();
        values.forEach(value -> grouped.computeIfAbsent(bucket(value.dataDate(), granularity).toString(), ignored -> new ArrayList<>()).add(value.dataValue()));
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        grouped.forEach((date, numbers) -> {
            BigDecimal value;
            if ("BOOLEAN".equalsIgnoreCase(unit))
                value = numbers.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            else if ("VOLUME".equalsIgnoreCase(unit))
                value = numbers.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            else
                value = numbers.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(numbers.size()), 8, RoundingMode.HALF_UP);
            result.put(date, value);
        });
        return result;
    }

    /** 将任意日期归一到所属训练周期的起始日期，用于基础数据和动态指标对齐。 */
    private LocalDate bucket(LocalDate date, String granularity) {
        return switch (granularity == null ? "DAY" : granularity) {
            case "MONTH" -> date.withDayOfMonth(1);
            case "TENDAY" -> date.withDayOfMonth(date.getDayOfMonth() <= 10 ? 1 : date.getDayOfMonth() <= 20 ? 11 : 21);
            case "YEAR" -> date.withDayOfYear(1);
            default -> date;
        };
    }

    private LocalDate parseDate(String value) {
        return LocalDate.parse(value.substring(0, 10));
    }

    /** 根据本次训练的业务范围构造钢联研报查询问题。 */
    private String buildReportQuery(ModelTrainConfigTb config, LocalDate begin, LocalDate end) {
        return "请分析" + begin + "至" + end + "期间" + text(config.getRegionName(), "全部区域") + text(config.getIndustryName(), "天然气") + "市场中可能影响天然气销量的供应、需求、库存、价格、政策、运输和市场情绪因素，并提供可用于识别指标的事实依据。";
    }

    private String text(String value, String fallback) {
        return TextUtils.hasText(value) ? value : fallback;
    }

    /** 兼容大模型返回纯 JSON 或 ```json 代码块两种形式。 */
    private JsonNode parseJson(String value) {
        try {
            String normalized = value.trim();
            if (normalized.startsWith("```"))
                normalized = normalized.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
            return objectMapper.readTree(normalized);
        } catch (Exception exception) {
            throw new BusinessException("大模型未返回合法JSON：" + exception.getMessage());
        }
    }

    /** 将未采用的指标及原因写入训练快照，不静默丢弃。 */
    private void exclude(ArrayNode target, JsonNode definition, MysteelIndexCandidate candidate, String reason) {
        ObjectNode item = target.addObject().put("indicator_name", definition.path("indicator_name").asText("")).put("reason", reason);
        if (candidate != null)
            item.put("index_code", candidate.indexCode()).put("index_name", candidate.indexName());
    }

    /** 构造关闭或失败时的空动态特征结果。 */
    private DynamicFeatureResult empty(String status, String error) {
        ObjectNode snapshot = objectMapper.createObjectNode().put("status", status);
        if (TextUtils.hasText(error))
            snapshot.put("error", error);
        return new DynamicFeatureResult(Map.of(), snapshot);
    }

    /** 将纳秒计时转换为毫秒，统一用于各步骤耗时日志。 */
    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
