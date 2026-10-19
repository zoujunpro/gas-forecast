package com.gas.forecast.business.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.security.context.SecurityContextHolder;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.AiConversationMessageTb;
import com.gas.forecast.dao.domain.AiConversationTb;
import com.gas.forecast.dao.domain.ModelForecastRecordTb;
import com.gas.forecast.dao.mapper.AiConversationMessageTbMapper;
import com.gas.forecast.dao.mapper.AiConversationTbMapper;
import com.gas.forecast.dao.mapper.ModelForecastRecordTbMapper;
import java.util.Date;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/** 管理预测批次与预测分析会话的确定性绑定和分析消息归档。 */
@Service
@RequiredArgsConstructor
public class ForecastConversationService {
    private final AiConversationTbMapper conversationMapper;
    private final AiConversationMessageTbMapper messageMapper;
    private final ModelForecastRecordTbMapper forecastRecordMapper;
    private final ForecastAnalysisService forecastAnalysisService;

    public AiConversationTb require(String forecastBatchNo, String conversationNo) {
        if (!TextUtils.hasText(forecastBatchNo)) throw new BusinessException("预测批次号不能为空");
        if (TextUtils.hasText(conversationNo)) {
            AiConversationTb supplied = findOwned(conversationNo);
            if (!"FORECAST_ANALYSIS".equals(supplied.getScene()) || !forecastBatchNo.equals(supplied.getBusinessNo()))
                throw new BusinessException("当前会话不属于该预测批次");
            return supplied;
        }
        AiConversationTb existing = findByBatch(forecastBatchNo);
        if (existing == null) {
            ModelForecastRecordTb forecast = forecastRecordMapper.selectOne(Wrappers.<ModelForecastRecordTb>lambdaQuery()
                    .eq(ModelForecastRecordTb::getForecastBatchNo, forecastBatchNo).last("limit 1"));
            if (forecast == null) throw new BusinessException("预测批次不存在：" + forecastBatchNo);
            try { existing = create(forecast); }
            catch (DuplicateKeyException exception) { existing = findByBatch(forecastBatchNo); }
        }
        archiveAutomaticAnalysis(existing);
        return existing;
    }

    private AiConversationTb findByBatch(String forecastBatchNo) {
        return conversationMapper.selectOne(Wrappers.<AiConversationTb>lambdaQuery().eq(AiConversationTb::getCreatedBy, currentUserId())
                .eq(AiConversationTb::getScene, "FORECAST_ANALYSIS").eq(AiConversationTb::getBusinessType, "FORECAST")
                .eq(AiConversationTb::getBusinessNo, forecastBatchNo).last("limit 1"));
    }

    private AiConversationTb create(ModelForecastRecordTb forecast) {
        Date now = new Date(); AiConversationTb value = new AiConversationTb();
        value.setConversationNo("AICHAT-" + UUID.randomUUID().toString().replace("-", ""));
        value.setTitle((TextUtils.hasText(forecast.getCustomerName()) ? forecast.getCustomerName() : "预测结果") + " AI分析");
        value.setAgentCode(forecast.getAgentCode()); value.setScene("FORECAST_ANALYSIS"); value.setBusinessType("FORECAST");
        value.setBusinessNo(forecast.getForecastBatchNo()); value.setLastForecastBatchNo(forecast.getForecastBatchNo()); value.setStatus("ACTIVE");
        value.setCreatedBy(currentUserId()); value.setCreatedAt(now); value.setUpdatedAt(now); conversationMapper.insert(value); return value;
    }

    private void archiveAutomaticAnalysis(AiConversationTb conversation) {
        long count = messageMapper.selectCount(Wrappers.<AiConversationMessageTb>lambdaQuery()
                .eq(AiConversationMessageTb::getConversationId, conversation.getId()).eq(AiConversationMessageTb::getMessageType, "FORECAST_AUTO_ANALYSIS"));
        if (count > 0) return;
        var analysis = forecastAnalysisService.get(conversation.getBusinessNo());
        if (analysis == null || !"SUCCESS".equals(analysis.analysisStatus()) || !TextUtils.hasText(analysis.analysisContent())) return;
        AiConversationMessageTb message = new AiConversationMessageTb();
        message.setConversationId(conversation.getId()); message.setRole("assistant"); message.setContent(analysis.analysisContent());
        message.setMessageType("FORECAST_AUTO_ANALYSIS"); message.setCreatedAt(new Date()); messageMapper.insert(message);
    }

    private AiConversationTb findOwned(String conversationNo) {
        AiConversationTb value = conversationMapper.selectOne(Wrappers.<AiConversationTb>lambdaQuery()
                .eq(AiConversationTb::getConversationNo, conversationNo).eq(AiConversationTb::getCreatedBy, currentUserId()).last("limit 1"));
        if (value == null) throw new BusinessException("会话不存在或无权访问"); return value;
    }

    private String currentUserId() { return String.valueOf(SecurityContextHolder.getUserId()); }
}
