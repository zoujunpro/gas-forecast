package com.gas.forecast.ai.service;

import com.gas.forecast.ai.model.AiIntent;
import com.gas.forecast.ai.model.AiToolName;
import com.gas.forecast.common.util.TextUtils;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** 对大模型提取结果做确定性校验，不能依赖模型自行判断参数是否完整。 */
@Component
public class AiToolArgumentValidator {
    private final AiToolRegistry registry;

    public AiToolArgumentValidator(AiToolRegistry registry) {
        this.registry = registry;
    }

    public List<String> validate(AiIntent intent) {
        List<String> errors = new ArrayList<>();
        AiToolName tool = intent.getTool() == null ? AiToolName.NONE : intent.getTool();
        for (String argument : registry.definition(tool).requiredArguments()) {
            if ("customer".equals(argument) && !TextUtils.hasText(intent.getCustomerCode()) && !TextUtils.hasText(intent.getCustomerName()))
                errors.add("缺少客户名称 customerName");
            if ("scope".equals(argument) && !TextUtils.hasText(intent.getCustomerCode()) && !TextUtils.hasText(intent.getCustomerName()) && !TextUtils.hasText(intent.getRegionCode())
                    && !TextUtils.hasText(intent.getRegionName()) && !TextUtils.hasText(intent.getIndustryCode()) && !TextUtils.hasText(intent.getIndustryName()))
                errors.add("缺少训练数据范围，需要客户、地区或行业中的至少一项");
        }
        if (intent.getForecastHorizon() != null && (intent.getForecastHorizon() < 1 || intent.getForecastHorizon() > 366))
            errors.add("forecastHorizon必须在1到366之间");
        if (TextUtils.hasText(intent.getStartDate()) && !intent.getStartDate().matches("\\d{4}-\\d{2}-\\d{2}"))
            errors.add("startDate必须使用yyyy-MM-dd格式");
        if (TextUtils.hasText(intent.getEndDate()) && !intent.getEndDate().matches("\\d{4}-\\d{2}-\\d{2}"))
            errors.add("endDate必须使用yyyy-MM-dd格式");
        return errors;
    }
}
