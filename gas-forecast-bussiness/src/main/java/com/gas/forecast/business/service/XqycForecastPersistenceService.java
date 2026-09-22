package com.gas.forecast.business.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gas.forecast.dao.domain.ModelForecastResultTb;
import com.gas.forecast.dao.mapper.ModelForecastResultTbMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class XqycForecastPersistenceService {

    private static final DateTimeFormatter BATCH_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final ModelForecastResultTbMapper modelForecastResultTbMapper;
    private final ObjectMapper objectMapper;
    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    public XqycForecastPersistenceService(ModelForecastResultTbMapper modelForecastResultTbMapper,
                                          ObjectMapper objectMapper) {
        this.modelForecastResultTbMapper = modelForecastResultTbMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PersistSummary persist(String agentId, JsonNode request) throws Exception {
        return persist(agentId, request, BATCH_TIME_FORMAT.format(LocalDateTime.now()), false);
    }

    private PersistSummary persist(String agentId, JsonNode request, String batchTime, boolean replaceBatch) throws Exception {
        AgentFiles agentFiles = AgentFiles.from(agentId);
        Resource[] resources = resolver.getResources("classpath*:" + agentFiles.pattern());
        Arrays.sort(resources, Comparator.comparing(resource -> {
            String filename = resource.getFilename();
            return filename == null ? "" : filename;
        }));
        String requestedProvince = request.path("province").asText(request.path("region_name").asText(""));
        String requestedIndustry = normalizeIndustry(request.path("industry").asText(request.path("industry_name").asText("")));
        String requestedCustomer = request.path("customer").asText(request.path("customer_name").asText(""));
        AtomicInteger sequence = new AtomicInteger(1);
        int batchCount = 0;
        int resultCount = 0;

        for (Resource resource : resources) {
            JsonNode result = readResource(resource);
            String province = result.path("province").asText("");
            if (!requestedProvince.isBlank() && !requestedProvince.equals(province)) {
                continue;
            }

            FileParts fileParts = parseName(resource.getFilename());
            String industry = result.path("industry").asText(fileParts.industry() == null ? "全部行业" : fileParts.industry());
            String customer = result.path("customer").asText(fileParts.customer() == null ? "全部客户" : fileParts.customer());
            if (!requestedIndustry.isBlank() && !"全部行业".equals(requestedIndustry)
                    && !requestedIndustry.equals(normalizeIndustry(industry))) {
                continue;
            }
            if (!requestedCustomer.isBlank() && !"全部客户".equals(requestedCustomer)
                    && !requestedCustomer.equals(customer)) {
                continue;
            }
            List<ModelForecastResultTb> points = buildResultRecords(agentId, batchTime, sequence.get(), result);
            if (points.isEmpty()) {
                continue;
            }

            String batchNo = batchNo(agentId, batchTime, sequence.get());
            if (replaceBatch) {
                deleteForecastResults(batchNo);
            }
            deleteForecastResults(batchNo);
            points.forEach(modelForecastResultTbMapper::insert);
            batchCount++;
            resultCount += points.size();
            sequence.incrementAndGet();
        }
        return new PersistSummary(batchCount, resultCount);
    }

    private void deleteForecastResults(String batchNo) {
        modelForecastResultTbMapper.delete(Wrappers.<ModelForecastResultTb>lambdaQuery()
                .eq(ModelForecastResultTb::getForecastBatchNo, batchNo));
    }

    private List<ModelForecastResultTb> buildResultRecords(String agentId,
                                                           String batchTime,
                                                           int sequence,
                                                           JsonNode result) {
        JsonNode dates = result.hasNonNull("future_dates") ? result.get("future_dates") : result.get("dates");
        JsonNode values = result.hasNonNull("future_predicted") ? result.get("future_predicted") : result.get("predicted");
        List<ModelForecastResultTb> records = new ArrayList<>();
        if (dates == null || values == null || !dates.isArray() || !values.isArray()) {
            return records;
        }
        int size = Math.min(dates.size(), values.size());
        String batchNo = batchNo(agentId, batchTime, sequence);
        for (int i = 0; i < size; i++) {
            JsonNode date = dates.get(i);
            JsonNode value = values.get(i);
            if (date == null || value == null || value.isNull()) {
                continue;
            }
            ModelForecastResultTb record = new ModelForecastResultTb();
            record.setForecastBatchNo(batchNo);
            record.setForecastDate(LocalDate.parse(date.asText()).toString());
            record.setForecastValue(new BigDecimal(value.asText()));
            records.add(record);
        }
        return records;
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

    private String normalizeIndustry(String industry) {
        return switch (industry) {
            case "天然气化工" -> "化工";
            case "天然气发电" -> "发电";
            default -> industry;
        };
    }

    private String batchNo(String agentId, String batchTime, int sequence) {
        String prefix = switch (agentId) {
            case "winter-supply" -> "WSFC";
            case "monthly-sales" -> "MSFC";
            case "short-term" -> "STFC";
            default -> "XQFC";
        };
        return String.format(Locale.ROOT, "%s-%s-%03d", prefix, batchTime, sequence);
    }

    private record FileParts(String industry, String customer) {
    }

    private record AgentFiles(String pattern) {
        private static AgentFiles from(String agentId) {
            return switch (agentId) {
                case "monthly-sales" -> new AgentFiles("xqyc/result_data/*.json");
                case "short-term" -> new AgentFiles("xqyc/short_term_data/*.json");
                case "winter-supply" -> new AgentFiles("xqyc/winter_supply_data/*.json");
                default -> new AgentFiles("xqyc/winter_supply_data/*.json");
            };
        }
    }

    public record PersistSummary(int batchCount, int resultCount) {
    }
}
