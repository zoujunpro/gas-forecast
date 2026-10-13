package com.gas.forecast;

import static org.junit.jupiter.api.Assertions.fail;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.business.dto.response.ModelForecastAnalysisResponse;
import com.gas.forecast.business.enums.ModelForecastStatus;
import com.gas.forecast.business.service.ForecastAnalysisService;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.ModelForecastRecordTb;
import com.gas.forecast.dao.mapper.ModelForecastRecordTbMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** 一次性补齐历史成功预测的大模型分析；必须显式指定开关才会执行并写数据库。 */
@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "spring.config.import=optional:file:../.env[.properties]")
class ForecastAnalysisBackfillTest {
    private static final String SUCCESS = "SUCCESS";
    private static final String FAILED = "FAILED";
    private static final Duration TIMEOUT = Duration.ofMinutes(10);

    @Autowired
    private ForecastAnalysisService forecastAnalysisService;
    @Autowired
    private ModelForecastRecordTbMapper forecastRecordMapper;

    @Test
    @EnabledIfSystemProperty(named = "gas.analysis.backfill.enabled", matches = "true")
    void backfillSuccessfulForecastsWithoutAnalysis() throws Exception {
        List<String> successfulBatches = forecastRecordMapper.selectList(Wrappers.<ModelForecastRecordTb>lambdaQuery().select(ModelForecastRecordTb::getForecastBatchNo)
                .eq(ModelForecastRecordTb::getStatus, ModelForecastStatus.SUCCESS.getCode()).orderByAsc(ModelForecastRecordTb::getId)).stream().map(ModelForecastRecordTb::getForecastBatchNo)
                .filter(TextUtils::hasText).toList();

        List<String> pendingBatches = successfulBatches.stream().filter(batchNo -> {
            ModelForecastAnalysisResponse analysis = forecastAnalysisService.get(batchNo);
            return analysis == null || !SUCCESS.equals(analysis.analysisStatus());
        }).toList();
        log.info("发现 {} 个成功预测批次，其中 {} 个需要补充分析", successfulBatches.size(), pendingBatches.size());

        pendingBatches.forEach(batchNo -> forecastAnalysisService.submit(batchNo, true));
        awaitCompletion(pendingBatches);
    }

    private void awaitCompletion(List<String> batchNos) throws Exception {
        Instant deadline = Instant.now().plus(TIMEOUT);
        Map<String, ModelForecastAnalysisResponse> latest = new LinkedHashMap<>();
        while (Instant.now().isBefore(deadline)) {
            latest.clear();
            for (String batchNo : batchNos)
                latest.put(batchNo, forecastAnalysisService.get(batchNo));
            boolean completed = latest.values().stream().allMatch(this::terminal);
            if (completed)
                break;
            Thread.sleep(1000);
        }

        Map<String, String> failures = latest.entrySet().stream().filter(entry -> entry.getValue() == null || !SUCCESS.equals(entry.getValue().analysisStatus()))
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> describe(entry.getValue()), (left, right) -> left, LinkedHashMap::new));
        if (!failures.isEmpty())
            fail("历史预测分析回填未全部成功：" + failures);
        log.info("历史预测分析回填完成，共成功入库 {} 个批次", batchNos.size());
    }

    private boolean terminal(ModelForecastAnalysisResponse analysis) {
        return analysis != null && (SUCCESS.equals(analysis.analysisStatus()) || FAILED.equals(analysis.analysisStatus()));
    }

    private String describe(ModelForecastAnalysisResponse analysis) {
        if (analysis == null)
            return "未生成分析记录";
        return analysis.analysisStatus() + (TextUtils.hasText(analysis.errorMessage()) ? "：" + analysis.errorMessage() : "");
    }
}
